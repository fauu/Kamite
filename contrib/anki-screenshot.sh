#!/usr/bin/env bash

#
# Latest Anki card: add area screenshot and sentence
#
# Basic invocation:
#   Prompt the user for an area selection, take a screenshot of it and update the Anki note
#   corresponding to the most recent card. The screenshot will be converted to WebP and downscaled
#   if necessary ($PICTURE_RESIZE_GEOMETRY).
#
# Optional arguments:
#   -s <sentence> Set the card's "Sentence" field ($SENTENCE_FIELD_NAME) to the specified value.
#   -g <geometry> Instead of prompting for selection, take a screenshot of the specified area.
#                 The geometry format is "X,Y WxH" (e.g. "20,-100 1000x600").
#   -w <window_name> [Currently Sway only] Instead of prompting for selection, take a screenshot of
#                    a first window with the provided name. The window must already be visible on
#                    the screen.
#                    The window name specifier is treated as a regular expression.
#   -r <ratio>    [Only with -w] Crop the screenshot area to a rectangle of a specified ratio
#                 centered inside the given window. Useful for games with a fixed aspect ratio.
#                 The ratio format is "W:H" (e.g. "16:9").
#   -o <offsets>  [Only with -w] Shrink the window screenshot area by the specified pixel offsets.
#                 Applies before -r. Useful for games with a menu bar.
#                 The offsets format is "TOP:RIGHT:BOTTOM:LEFT" (e.g. "15,0,0,0").
#
# Exit status:
#   0    Updated note
#   >0   Failed to update note
#
# Dependencies:
#   grim + slurp (Wayland) / maim (Xorg), jq, curl, imagemagick, Anki Connect, libnotify,
#   bc (-r option only)
#

# Config
PICTURE_FIELD_NAME="Picture" # !!!
SENTENCE_FIELD_NAME="Sentence" # !!!
PICTURE_RESIZE_GEOMETRY='x350>' # See: https://legacy.imagemagick.org/Usage/resize/#shrink
ANKI_CONNECT_ENDPOINT='localhost:8765'

anki_cmd() {
  local cmd='{ "action": "'"$1"'", "version": 6, "params": { '"$2"' } }'
  curl "$ANKI_CONNECT_ENDPOINT" -s -X POST -d "$cmd"
}

notify() {
  notify-send 'Anki screenshot' "$1"
}

GEOMETRY_RE="^(-?[[:digit:]]+),(-?[[:digit:]]+) ([[:digit:]]+)x([[:digit:]]+)$"
RATIO_RE="^([[:digit:]]+):([[:digit:]]+)$"
OFFSETS_RE="^([[:digit:]]+),([[:digit:]]+),([[:digit:]]+),([[:digit:]]+)$"

main() {
  while getopts ":s:g:w:r:o:" opt; do
    case $opt in
      s) sentence="$OPTARG"
      ;;
      g) geometry="$OPTARG"
      ;;
      w) window_name="$OPTARG"
      ;;
      r) ratio="$OPTARG"
      ;;
      o) offsets="$OPTARG"
      ;;
      \?) echo "Invalid option -$OPTARG" >&2
      exit 1
      ;;
    esac
  done

  if [[ -n $geometry ]]; then
    if [[ "$geometry" =~ $GEOMETRY_RE ]]; then
      x=${BASH_REMATCH[1]}
      y=${BASH_REMATCH[2]}
      w=${BASH_REMATCH[3]}
      h=${BASH_REMATCH[4]}
    else
      notify 'Geometry format incorrect'
      exit 2
    fi
  fi

  local last_note_id
  last_note_id=$(anki_cmd 'findNotes' '"query": "added:2"' | jq '.result | max')
  if [[ -z $last_note_id || -n ${last_note_id//[0-9]/} ]]; then
    notify 'Could not get last note ID. Is Anki running?'
    exit 3
  fi

  local ss_cmd
  local geometry_part
  if test -n "${WAYLAND_DISPLAY-}"; then
    if [[ $x ]]; then
      geometry_part="$geometry"
    elif [[ $window_name ]]; then
      local window_info
      window_info=$(swaymsg -t get_tree | jq -r '[.. |try select(.name|test("'"$window_name"'")?)][0]')
      if [[ "$window_info" == "null" ]]; then
        notify "Window '$window_name' not found"
        exit 6
      fi
      local wox
      local woy
      local wrx
      local wry
      local wx
      local wy
      local ww
      local wh
      read -r wox woy wrx wry ww wh <<<"$(echo "$window_info" \
        | jq -r '"\(.rect.x) \(.rect.y) \(.window_rect.x) \(.window_rect.y) \(.window_rect.width) \(.window_rect.height)"')"
      wx=$((wox+wrx))
      wy=$((woy+wry))

      if [[ -n "$offsets" ]]; then
        local otop
        local oright
        local obottom
        local oleft
        if [[ "$offsets" =~ $OFFSETS_RE ]]; then
          otop=${BASH_REMATCH[1]}
          oright=${BASH_REMATCH[2]}
          obottom=${BASH_REMATCH[3]}
          oleft=${BASH_REMATCH[4]}
        else
          notify 'Offsets format incorrect'
          exit 8
        fi
        wx=$((wx+oleft))
        wy=$((wy+otop))
        ww=$((ww-oleft-oright))
        wh=$((wh-otop-obottom))
      fi

      if [[ -n $ratio ]]; then
        if [[ "$ratio" =~ $RATIO_RE ]]; then
          rw=${BASH_REMATCH[1]}
          rh=${BASH_REMATCH[2]}
          local r
          local wr
          r=$(echo "$rw/$rh" | bc -l)
          wr=$(echo "$ww/$wh" | bc -l)
          local cx
          local cy
          local cw
          local ch
          if (( $(echo "$r > $wr" | bc -l) )); then
            cx="$wx"
            cw="$ww"
            ch=$(echo "(1/$r)*$cw" | bc -l | xargs printf '%.0f\n')
            local h_half_delta
            h_half_delta=$(((wh-ch)/2))
            cy=$((wy+h_half_delta))
          else
            cy="$wy"
            ch="$wh"
            cw=$(echo "$r*$ch" | bc -l | xargs printf '%.0f\n')
            local w_half_delta
            w_half_delta=$(((ww-cw)/2))
            cx=$((wx+w_half_delta))
            echo "$cx,$cy $cw $ch"
          fi

          geometry_part="$cx,$cy ${cw}x$ch"
        else
          notify 'Ratio format incorrect'
          exit 7
        fi
      else
        geometry_part="$wx,$wy ${ww}x$wh"
      fi
    else
      # shellcheck disable=SC2016
      geometry_part='$(slurp)'
    fi
    ss_cmd='grim -g "'"$geometry_part"'" -'
  else
    if [[ $x ]]; then
      geometry_part="-g $(printf "%dx%d%+d%+d" "$w" "$h" "$x" "$y")"
    else
      geometry_part='-s'
    fi
    ss_cmd="maim $geometry_part"
  fi

  local picture_data
  picture_data=$(eval "$ss_cmd" \
    | convert - -resize ${PICTURE_RESIZE_GEOMETRY} webp:- \
    | base64 \
    | tr -d \\n)
  if [[ -z $picture_data ]]; then
    notify 'Screenshot cancelled'
    exit 4
  fi

  local picture_filename
  picture_filename="$(uuidgen | tr -d '-').webp"

  local sentence_part
  if [[ -n $sentence ]]; then
    sentence_part=', "'"$SENTENCE_FIELD_NAME"'": "'"${sentence//$'\n'/\\n}"'"'
  fi

  local params
  params=$(printf '%s' \
    '"note": { '\
      '"id": '"$last_note_id"', '\
      '"fields": { "'"$PICTURE_FIELD_NAME"'": ""'"$sentence_part"' }, '\
      '"picture": [{ '\
        '"data": "'"$picture_data"'", '\
        '"filename": "'"$picture_filename"'", '\
        '"fields": ["'"$PICTURE_FIELD_NAME"'"] '\
      '}] '\
    '}')
  local error
  error=$(anki_cmd 'updateNoteFields' "$params" | jq '.error')
  if [ "$error" = "null" ]; then
    notify 'Updated picture'
  else
    notify 'Failed to update picture'
    exit 5
  fi
}

main "$@"
