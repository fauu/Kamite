#!/usr/bin/env bash

set -u
if [[ "${TRACE-0}" == "1" ]]; then set -x; fi

help() {
  echo 'Usage:
    edge-tts-cached.sh <voice_name> <text> [rest_argument_passed_to_edgetts...]

Read <text> aloud using <voice_name> through the Microsoft Edge TTS API. Caches last text and audio,
so that subsequent consecutive replays of the same text using the same voice are instantaneous.

Dependencies: edge-tts, mpv'
}

: "${TMPDIR:=/tmp}"
LAST_AUDIO_FILEPATH="$TMPDIR/edge-tts-last-audio.mp3"
LAST_TEXT_FILEPATH="$TMPDIR/edge-tts-last-text.txt"

if [[ "${1-}" =~ ^-*h(elp)?$ ]]; then help; exit; fi

VOICE="${1-}"
TEXT="${2-}"
REST_ARGS="${3-}"

if [[ ! "$VOICE" || ! "$TEXT" ]]; then
  help
  exit
fi

play_last_audio() {
  mpv "$LAST_AUDIO_FILEPATH"
}

main() {
  local last_text
  last_text=$(cat "$LAST_TEXT_FILEPATH")
  if [[ $TEXT == "$last_text" && -f "$LAST_AUDIO_FILEPATH" ]]; then
    play_last_audio
  else
    echo "$TEXT" > "$LAST_TEXT_FILEPATH"
    if edge-tts --voice "$VOICE" --text "$TEXT" \
      --write-media "$LAST_AUDIO_FILEPATH" \
      ${REST_ARGS:+"$REST_ARGS"}; then
        play_last_audio
    else
      echo "edge-tts has failed"
    fi
  fi
}

main
