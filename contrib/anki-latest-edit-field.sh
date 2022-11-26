#!/usr/bin/env bash

#
# Latest Anki card: edit specified field in a popup window
#
#   Display a popup window with a form allowing to edit the text content of a specified field of an
#   Anki note corresponding to the most recent card. The field name is read from the first
#   positional argument to the script.
#
# Dependencies:
#   jq, curl, yad, Anki Connect, libnotify
#

# Config
ANKI_CONNECT_ENDPOINT='localhost:8765'
WINDOW_WIDTH=300
WINDOW_HEIGHT=200

anki_cmd() {
  local cmd='{ "action": "'"$1"'", "version": 6, "params": { '"$2"' } }'
  curl "$ANKI_CONNECT_ENDPOINT" -s -X POST -d "$cmd"
}

notify() {
  notify-send 'Anki note field edit' "$1"
}

main() {
  local field_name="$1"
  if [ ! "$field_name" ]; then
    notify "Unspecified field name"
    exit
  fi

  local last_note_id
  last_note_id=$(anki_cmd 'findNotes' '"query": "added:2"' | jq '.result | max')
  if [[ -z $last_note_id || -n ${last_note_id//[0-9]/} ]]; then
    notify 'Could not get last note ID'
    return
  fi

  local last_note_info
  last_note_info=$(anki_cmd 'notesInfo' '"notes": ['"$last_note_id"']' | jq -r ".result[0]")
  if [[ $last_note_info == "{}" ]]; then
    notify 'Got empty last note info'
    return
  fi

  local current_value
  current_value=$(echo "$last_note_info" \
    | jq -r ".fields[\"$field_name\"].value" \
    | jq -Rr @html)
  if [[ $current_value == "null" ]]; then
    notify 'Field does not exist'
    return
  fi

  local new_value
  new_value=$(yad --title="Edit Anki Note" \
    --width $WINDOW_WIDTH --height $WINDOW_HEIGHT --center \
    --form \
    --field="$field_name:TXT" --separator="" "$current_value")

  # shellcheck disable=SC2181
  if [ "$?" -ne 0 ]; then
    # Aborted
    exit
  fi

  if [ "$new_value" == "$current_value" ]; then
    notify "Field unchanged"
    exit
  fi

  local params
  params=$(printf '%s' \
    '"note": { '\
      '"id": '"$last_note_id"', '\
      '"fields": { "'"$field_name"'": "'"$new_value"'" }' \
    '}')
  local error
  error=$(anki_cmd 'updateNoteFields' "$params" | jq '.error')
  if [ "$error" = "null" ]; then
    notify 'Updated field'
  else
    notify 'Failed to update field'
  fi
}

main "$@"
