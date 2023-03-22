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
#   -w <window_name> Instead of prompting for selection, take a screenshot of a first window with
#                    the provided name. The window must already be visible on the screen.
#                    The window name specifier is treated as a regular expression.
#                    NOTE: On Wayland, this option is only supported with Sway.
#   -r <ratio>    [Only with -w] Crop the screenshot area to a rectangle of a specified ratio
#                 centered inside the given window. Useful for games with a fixed aspect ratio.
#                 The ratio format is "W:H" (e.g. "16:9").
#   -o <offsets>  [Only with -w] Shrink the window screenshot area by the specified pixel offsets.
#                 Applies before -r. Useful for games with a menu bar.
#                 The offsets format is "TOP:RIGHT:BOTTOM:LEFT" (e.g. "15,0,0,0").
#
# Exit status:
#   0     Updated note
#   0-99  Failed to update note
#   100   Stopped after producing the requested debug output
#
# Dependencies:
#   grim + slurp (Wayland) / maim (Xorg), jq, curl, imagemagick, Anki Connect, libnotify,
#   bc (-r option only), xdotool (-w option on Xorg only)
#

# Config
PICTURE_FIELD_NAME="Picture" # !!!
SENTENCE_FIELD_NAME="Sentence" # !!!
PICTURE_RESIZE_GEOMETRY='x350>' # See: https://legacy.imagemagick.org/Usage/resize/#shrink
ANKI_CONNECT_ENDPOINT='localhost:8765'

GEOMETRY_RE="^(-?[[:digit:]]+),(-?[[:digit:]]+) ([[:digit:]]+)x([[:digit:]]+)$"
RATIO_RE="^([[:digit:]]+):([[:digit:]]+)$"
OFFSETS_RE="^([[:digit:]]+),([[:digit:]]+),([[:digit:]]+),([[:digit:]]+)$"

main() {
  parse_args "$@"

  if [[ -n $geometry ]]; then
    parse_geometry
  fi

  # NOTE: Using global variables instead of subshell outputs to simplify exiting on errors
  get_anki_last_note_id # out `last_note_id`

  make_screenshot_command # out `ss_cmd`

  if [[ $DEBUG == "ss" ]]; then
    eval "$ss_cmd" | display
    exit 100
  fi

  do_screenshot # out `picture_data`

  picture_filename="$(uuidgen | tr -d '-').webp"

  local sentence_part
  if [[ -n $sentence ]]; then
    sentence_part=', "'"$SENTENCE_FIELD_NAME"'": "'"${sentence//$'\n'/\\n}"'"'
  fi

  update_anki_note
}

anki_cmd() {
  local cmd='{ "action": "'"$1"'", "version": 6, "params": { '"$2"' } }'
  curl "$ANKI_CONNECT_ENDPOINT" -s -X POST -d "$cmd"
}

notify() {
  notify-send 'Anki screenshot' "$1"
}

parse_args() {
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
}

parse_geometry() {
  if [[ "$geometry" =~ $GEOMETRY_RE ]]; then
    x=${BASH_REMATCH[1]}
    y=${BASH_REMATCH[2]}
    w=${BASH_REMATCH[3]}
    h=${BASH_REMATCH[4]}
  else
    notify 'Geometry format incorrect'
    exit 2
  fi
}

get_anki_last_note_id() {
  last_note_id=$(anki_cmd 'findNotes' '"query": "added:2"' | jq '.result | max')
  if [[ -z $last_note_id || -n ${last_note_id//[0-9]/} ]]; then
    notify 'Could not get last note ID. Is Anki running?'
    exit 3
  fi
}

make_screenshot_command() {
  if test -n "${WAYLAND_DISPLAY-}"; then
    if [[ $x ]]; then
      geometry_part="$geometry"
    elif [[ $window_name ]]; then
      window_name_to_geometry_part_sway # out `geometry_part`
    else
      # shellcheck disable=SC2016
      geometry_part='$(slurp)'
    fi
    ss_cmd='grim -g "'"$geometry_part"'" -'
  else
    if [[ $x ]]; then
      geometry_part="-g $(printf "%dx%d%+d%+d" "$w" "$h" "$x" "$y")"
    elif [[ $window_name ]]; then
      window_name_to_geometry_part_xorg # out `geometry_part`
    else
      geometry_part='-s'
    fi
    ss_cmd="maim $geometry_part"
  fi
}

window_name_to_geometry_part_sway() {
  local window_info
  window_info=$(swaymsg -t get_tree | jq -r '[.. |try select(.name|test("'"$window_name"'")?)][0]')
  if [[ "$window_info" == "null" ]]; then
    exit_window_not_found
  fi
  read -r wox woy wrx wry ww wh drh <<<"$(echo "$window_info" \
    | jq -r '"\(.rect.x) \(.rect.y) \(.window_rect.x) \(.window_rect.y) \(.window_rect.width) \(.window_rect.height) \(.deco_rect.height)"')"
  wx=$((wox+wrx))
  wy=$((woy+wry-drh)) # Minus `deco_rect.height` because the titlebar is double-counted in `rect.y`
                      # and `window_rect.y`

  window_name_to_geometry_part_common # out `gpx` `gpy` `gpw` `gph`
  geometry_part="$gpx,$gpy ${gpw}x$gph"
}

window_name_to_geometry_part_xorg() {
  local wid
  wid=$(xdotool search --name "$window_name" | head -n1)
  if [[ ! $wid ]]; then
    echo "NOT FOUND"
    exit_window_not_found
  fi

  declare -A wg
  while IFS='=' read -r name value; do
    wg["$name"]="$value"
  done < <(xdotool getwindowgeometry --shell "$wid")
  wx="${wg[X]}"
  wy="${wg[Y]}"
  ww="${wg[WIDTH]}"
  wh="${wg[HEIGHT]}"

  window_name_to_geometry_part_common # out `gpx` `gpy` `gpw` `gph`
  geometry_part="-g $(printf "%dx%d%+d%+d" "$gpw" "$gph" "$gpx" "$gpy")" # QUAL: DRY
}

exit_window_not_found() {
  notify "Window '$window_name' not found"
  exit 6
}

window_name_to_geometry_part_common() {
  if [[ -n "$offsets" ]]; then
    local otop oright obottom oleft
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
      local r wr
      r=$(echo "$rw/$rh" | bc -l)
      wr=$(echo "$ww/$wh" | bc -l)
      # Cropped window rectangle
      local cx cy cw ch
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
      fi
      gpx="$cx"
      gpy="$cy"
      gpw="$cw"
      gph="$ch"
    else
      notify 'Ratio format incorrect'
      exit 7
    fi
  else
    gpx="$wx"
    gpy="$wy"
    gpw="$ww"
    gph="$wh"
  fi
}

do_screenshot() {
  picture_data=$(eval "$ss_cmd" \
    | convert - -resize ${PICTURE_RESIZE_GEOMETRY} webp:- \
    | base64 \
    | tr -d \\n)
  if [[ -z $picture_data ]]; then
    notify 'Screenshot cancelled'
    exit 4
  fi
}

update_anki_note() {
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
