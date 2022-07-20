require"os"
require"io"
require"string"

local utils = require"mp.utils"

local port = 4110

local Command = {
  SHOW_CHUNK = 1,
  SHOW_CHUNK_TRANSLATION = 2,
}

local command_name = {
  [Command.SHOW_CHUNK] = "show",
  [Command.SHOW_CHUNK_TRANSLATION] = "show-translation"
}

local command_text_field = {
  [Command.SHOW_CHUNK] = "chunk",
  [Command.SHOW_CHUNK_TRANSLATION] = "translation"
}

local dbus_available = false

local command_playback_time_field = "playbackTimeS"

function send_command(command, text)
  local params = utils.format_json({
    [command_text_field[command]] = text,
    [command_playback_time_field] = mp.get_property_native("playback-time"),
  })
  params = params:gsub("\"", "\\\"")
  params = params:gsub("\\\\\"", "\\\\\\\"") -- I love computers
  local os_cmd
  if dbus_available then
    os_cmd = "dbus-send --type=method_call"
      .. " --dest=io.github.kamitejp /Receiver io.github.kamitejp.Receiver.command"
      .. " string:'chunk_" .. command_name[command] .. "'"
      .. [[ string:"]] .. params .. [["]]
  else
    os_cmd = "curl -X POST -d"
      .. [[ "params=]] .. params .. [["]]
      .. " localhost:" .. port .. "/cmd/chunk/" .. command_name[command]
  end
  os.execute(os_cmd)
end

function handle_sub_text(_, t)
  if t ~= nil and t ~= "" then
    send_command(Command.SHOW_CHUNK, t)
  end
end

function handle_secondary_sub_text(_, t)
  if t ~= nil and t ~= "" then
    send_command(Command.SHOW_CHUNK_TRANSLATION, t)
  end
end

function check_dbus()
  local ret = os.execute("dbus-send")
  return ret / 256 == 1
end

function main()
  dbus_available = check_dbus()
  mp.observe_property("sub-text", "string", handle_sub_text)
  mp.observe_property("secondary-sub-text", "string", handle_secondary_sub_text)
end

main()
