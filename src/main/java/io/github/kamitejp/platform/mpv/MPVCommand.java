package io.github.kamitejp.platform.mpv;

public sealed interface MPVCommand
  permits MPVCommand.ObserveProperty,
          MPVCommand.PlayPause,
          MPVCommand.Seek,
          MPVCommand.SeekStartSub {
  record ObserveProperty(String name) implements MPVCommand {}
  record PlayPause() implements MPVCommand {}
  record Seek(int seconds) implements MPVCommand {}
  record SeekStartSub() implements MPVCommand {}

  default String toJSON() {
    return "{\"command\": ["
      + switch (this) {
        case ObserveProperty cmd ->
          "\"observe_property\", 0, \"%s\"".formatted(cmd.name());
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
