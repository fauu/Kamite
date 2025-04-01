package io.github.kamitejp.platform.mpv;

public sealed interface MpvCommand
  permits MpvCommand.ObserveProperty,
          MpvCommand.PlayPause,
          MpvCommand.Seek,
          MpvCommand.SeekStartSub {
  record ObserveProperty(String name) implements MpvCommand {}
  record PlayPause() implements MpvCommand {}
  record Seek(int seconds) implements MpvCommand {}
  record SeekStartSub() implements MpvCommand {}

  default String toJSON() {
    return "{\"command\": ["
      + switch (this) {
        case ObserveProperty cmd ->
          "\"observe_property\", 0, \"%s\"".formatted(cmd.name());
        case PlayPause _ ->
          "\"cycle\", \"pause\"";
        case Seek cmd ->
          "\"seek\", %s, \"exact\"".formatted(cmd.seconds);
        case SeekStartSub _ ->
          "\"sub-seek\", 0";
      }
      + "]}\n";
  }
}
