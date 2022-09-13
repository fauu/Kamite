#!/usr/bin/env bash

#
# Last Anki card: add area screenshot and sentence
#
# Basic invocation:
#   Prompt the user for an area selection, take a screenshot of it and update the most recent Anki
#   card with it. The screenshot will be converted to WebP and downscaled if necessary
#   ($PICTURE_RESIZE_GEOMETRY).
#
# Optional arguments:
#   -s <sentence>  Set the card's "Sentence" field ($SENTENCE_FIELD_NAME) to the specified value.
#   -g <geometry>  Instead of prompting for selection, take a screenshot of the specified area.
#                  The geometry format is "X,Y WxH" (e.g., "20,-100 1000x600").
#
# DEPENDENCIES: grim + slurp (Wayland) / maim (Xorg), jq, curl, imagemagick, Anki Connect, libnotify
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

main() {
  while getopts ":s:g:" opt; do
    case $opt in
      s) sentence="$OPTARG"
      ;;
      g) geometry="$OPTARG"
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
      notify "Geometry format incorrect. Aborting"
      exit
    fi
  fi

  local last_note_id
  last_note_id=$(anki_cmd 'findNotes' '"query": "added:2"' | jq '.result | max')
  if [[ -z $last_note_id || -n ${last_note_id//[0-9]/} ]]; then
    notify 'Could not get last note ID'
    return
  fi

  local ss_cmd
  local geometry_part
  if test -n "${WAYLAND_DISPLAY-}"; then
    if [[ $x ]]; then
      geometry_part="$geometry"
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
    # Cancelled
    return
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
  fi
}

main "$@"
