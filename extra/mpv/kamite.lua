require"os"
require"io"
require"string"

local utils = require"mp.utils"

local Command = {
  SHOW_CHUNK = 1,
  SHOW_CHUNK_TRANSLATION = 2,
}

local command_kind = {
  [Command.SHOW_CHUNK] = "chunk_show",
  [Command.SHOW_CHUNK_TRANSLATION] = "chunk_show-translation"
}

local command_text_field = {
  [Command.SHOW_CHUNK] = "chunk",
  [Command.SHOW_CHUNK_TRANSLATION] = "translation"
}

local command_playback_time_field = "playbackTimeS"

function send_command(command, text)
  local params = utils.format_json({
    [command_text_field[command]] = text,
    [command_playback_time_field] = mp.get_property_native("playback-time"),
  })
  params = params:gsub("\"", "\\\"")
  params = params:gsub("\\\\\"", "\\\\\\\"") -- I love computers
  local os_cmd = "dbus-send --type=method_call"
    .. " --dest=io.github.kamitejp /Receiver io.github.kamitejp.Receiver.command"
    .. " string:'" .. command_kind[command] .. "'"
    .. [[ string:"]] .. params .. [["]]
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

mp.observe_property("sub-text", "string", handle_sub_text)
mp.observe_property("secondary-sub-text", "string", handle_secondary_sub_text)
