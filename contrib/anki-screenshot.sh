#!/usr/bin/env bash

# Last anki card: add area screenshot and sentence
#
# Prompts the user to take a screenshot of a screen region, downscales it if necessary, converts it
# to webp and updates the most recent Anki card with it. Optionally, if a positional argument is
# provided to the script, it is also added to the card, in the card field determined by the variable
# SENTENCE_FIELD_NAME.
#
# DEPENDENCIES: grim + slurp (Wayland) / maim (Xorg), jq, curl, imagemagick, Anki Connect, libnotify

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

main() {
  local last_note_id
  last_note_id=$(anki_cmd 'findNotes' '"query": "added:2"' | jq '.result | max')
  if [[ -z $last_note_id || -n ${last_note_id//[0-9]/} ]]; then
    notify 'Could not get last node ID'
    return
  fi

  local ss_cmd
  if test -n "${WAYLAND_DISPLAY-}"; then
    # shellcheck disable=SC2016
    ss_cmd='grim -g "$(slurp)" -'
  else
    ss_cmd='maim -s'
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
  if [[ -n $1 ]]; then
    sentence_part=', "'"$SENTENCE_FIELD_NAME"'": "'"$1"'"'
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
