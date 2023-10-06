#!/usr/bin/env bash

#
# Kamite DeepLX: fetch DeepL translation of given JP text through DeepLX and send it to Kamite
#
#   Made to be executed as a Kamite custom command, e.g.:
#
#     deeplx {
#        symbol: DEP
#        name: DeepL lookup
#        command: ["/path/to/kamite-deeplx.sh", "EN", "{effectiveText}"]
#     }
#
#     (`EN` is the target language code)
#
#   Executing the above command from Kamite passes the current effective chunk text to this script,
#   which then sends back its translation to Kamite to be displayed in the chunk translation
#   component.
#
#   The script can also be specified as a Kamite `chunk-add` event handler (currently an
#   experimental, undocumented funtionality) to automatically fetch a translation each time a new
#   chunk is added:
#
#     events.handlers: [
#       { on: "chunk-add", exec: ["/path/to/kamite-deeplx.sh", "EN", "--event", "{eventData}"] }
#     ]
#
#   Note that such intensive use makes it more likely for your IP to be blocked by DeepL.
#
# Dependencies:
#   jq, curl, libnotify, DeeplX (must be running!)
#
#   DeepLX download: https://github.com/OwO-Network/DeepLX/releases
#

DEEPLX_ENDPOINT='localhost:1188/translate'
KAMITE_ENDPOINT='localhost:4110/cmd/chunk/show-translation'

NAME='kamite-deeplx'

if [ $# -lt 2 ]; then
  echo "USAGE: $NAME <target-language> <japanese-text> OR kamite-deeplx <target-language> --event <kamite-chunk-add-event-data>"
  notify-send "$NAME" 'Wrong number of parameters'
  exit 1
fi

if [[ "$2" == "--event" ]]; then
  text=$(jq -r '.chunkText' <<< "$3")
else
  text="$2"
fi

text=${text//$'\n'/}

res=$(curl -s -X POST \
  -d '{"text":"'"$text"'","source_lang":"JA","target_lang":"'"$1"'"}' \
  $DEEPLX_ENDPOINT)

# NOTE: Alternative translations (`alternatives`) are currently ignored
translation=$(jq -r '.data' <<< "$res")
if [[ $translation == 'null' ]]; then
  notify-send "$NAME" 'Error fetching translation'
  exit 1
fi

curl -i -X POST -d '{"translation":"'"$translation"'"}' "$KAMITE_ENDPOINT"
