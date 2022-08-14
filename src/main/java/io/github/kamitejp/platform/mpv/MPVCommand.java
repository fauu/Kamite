package io.github.kamitejp.platform.mpv;

public sealed interface MPVCommand
  permits MPVCommand.LoadKamiteScript,
          MPVCommand.InitKamiteScript,
          MPVCommand.ObservePause,
          MPVCommand.PlayPause,
          MPVCommand.Seek,
          MPVCommand.SeekStartSub {
  static final String KAMITE_SCRIPT_NAME = "kamite_mpv";

  record LoadKamiteScript(String path) implements MPVCommand {}
  record InitKamiteScript(int port) implements MPVCommand {}
  record ObservePause() implements MPVCommand {}
  record PlayPause() implements MPVCommand {}
  record Seek(int seconds) implements MPVCommand {}
  record SeekStartSub() implements MPVCommand {}

  default String toJSON() {
    return "{\"command\": ["
      + switch (this) {
        case LoadKamiteScript cmd ->
          "\"load-script\", \"%s\"".formatted(cmd.path);
        case InitKamiteScript cmd ->
          "\"script-message-to\", \"%s\", \"init\", \"%s\"".formatted(KAMITE_SCRIPT_NAME, cmd.port);
        case ObservePause ignored ->
          "\"observe_property\", 1, \"pause\"";
        case PlayPause ignored ->
          "\"cycle\", \"pause\"";
        case Seek cmd ->
          "\"seek\", %s, \"exact\"".formatted(cmd.seconds);
        case SeekStartSub ignored ->
          "\"sub-seek\", 0";
      }
      + "]}\n";
  }
}
