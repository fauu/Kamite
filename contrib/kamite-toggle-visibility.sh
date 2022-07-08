#!/usr/bin/env bash

# Toggles the visibility* of a browser window with an active Kamite tab.
# Supports Xorg (requires `wmctrl` and `xdotool`) and Sway.
#   When restoring visibility, changes the size and position of the window to
# those specified below. In Sway, it also moves the window to the workspace
# specified with the SWAY_WORKSPACE_NO variable.
#   *In Sway, the hiding is achieved by moving the window to the scratchpad.
# In Xorg, it is achieved by putting the game window specified with the
# ACTIVE_WINDOW_PATTERN variable in front of the browser window.
#   For ease of use, it is recommended to bind this command to a side mouse
# button or to a hot corner. Examples of hot corner programs are:
#   - waycorner (Wayland; https://github.com/icxes/waycorner - hot edge fork),
#   - cornora (Xorg; https://github.com/okitavera/cornora/).

# !!! SET DESIRED WINDOW DIMENSIONS
WINDOW_X=290
WINDOW_Y=0
WINDOW_WIDTH=1100
WINDOW_HEIGHT=940

BROWSER_TITLE_PATTERN="Kamite.*(Firefox|Chrome)"

toggle_for_sway() {
  # !!! SET DESIRED WORKSPACE FOR THE BROWSER WINDOW
  local WORKSPACE_NO=5

  local PROCESS_PATTERN="kamite"

  if ! pgrep -f "$PROCESS_PATTERN" &> /dev/null 2>&1; then
    exit
  fi

  local browser_in_scratch
  browser_in_scratch=$(
    swaymsg -t get_tree \
    | jq -r \
      'recurse(.nodes[])
        | select(.name == "__i3_scratch").floating_nodes[]
        | select(.name|test("'"$BROWSER_TITLE_PATTERN"'"))' \
    | wc -l
  )
  local selector
  selector="[title=\"$BROWSER_TITLE_PATTERN\"]"
  if [[ $browser_in_scratch -gt 0 ]] ; then
    swaymsg "$selector resize set width ${WINDOW_WIDTH}px height ${WINDOW_HEIGHT}px"
    swaymsg "$selector move position ${WINDOW_X} ${WINDOW_Y}"
    swaymsg "$selector move workspace $WORKSPACE_NO"
    swaymsg "$selector floating enable"
  else
    swaymsg "$selector move scratchpad"
  fi
}

toggle_for_xorg() {
  # This value works for games that are launched inside a Wine desktop, like so:
  #   wine explorer /desktop=example-name,1366x768 start /exec "/path/game.exe"
  local ACTIVE_WINDOW_PATTERN="Wine desktop"

  local active_window
  active_window=$(xdotool getactivewindow getwindowname)
  if [[ "$active_window" == *"$ACTIVE_WINDOW_PATTERN"* ]]; then
    local wid
    wid=$(wmctrl -l | grep -E "$BROWSER_TITLE_PATTERN" | tail -n1 | cut -d" " -f1)
    wmctrl -i -a "$wid"
    wmctrl -i -r "$wid" -e 0,${WINDOW_X},${WINDOW_Y},${WINDOW_WIDTH},${WINDOW_HEIGHT}
  else
    wmctrl -i -a "$(wmctrl -l | grep "$ACTIVE_WINDOW_PATTERN" | cut -d" " -f1)"
  fi
}

main() {
  if test -n "${WAYLAND_DISPLAY-}"; then
    toggle_for_sway
  else
    toggle_for_xorg
  fi
}

main
