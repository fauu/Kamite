#!/usr/bin/env bash

# Watches the clipboard and sends new text to Kamite.
#   Requires `curl`. Supports Xorg (requires `xsel` and `clipnotify`) and
# Wayland (except GNOME; requires `wl-clipboard`).
#   Xorg limitation: does not send the new text if it is equal to the previous
# clipboard text.

HOST="localhost:4110"

SHOW_CHUNK_ENDPOINT="$HOST/cmd/chunk/show"

watch_wl_clipboard() {
  wl-paste --watch bash -c \
    "xargs -r -0 -I{} curl -i -X POST -d 'params={\"chunk\":\"{}\"}' $SHOW_CHUNK_ENDPOINT"
}

x11_prev_text=""

watch_clipnotify() {
  while clipnotify;
  do
    local text
    text="$(xsel -b)"
    if [[ $text != "$x11_prev_text" ]]; then
      curl -i -X POST -d 'params={"chunk":"'"$text"'"}' "$SHOW_CHUNK_ENDPOINT"
      x11_prev_text=$text
    fi
  done
}

main() {
  if test -n "${WAYLAND_DISPLAY-}"; then
    watch_wl_clipboard
  else
    watch_clipnotify
  fi
}

main
