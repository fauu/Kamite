package io.github.kamitejp.platform.mpv;

public enum MPVCommand {
  OBSERVE_PAUSE,
  PLAYPAUSE,
  SEEK_BACK,
  SEEK_FORWARD,
  SEEK_START_SUB;

  public String toJSON() {
    return "{\"command\": ["
      + switch (this) {
        case OBSERVE_PAUSE  -> "\"observe_property\", 1, \"pause\"]";
        case PLAYPAUSE      -> "\"cycle\", \"pause\"]";
        case SEEK_BACK      -> "\"seek\", -1, \"exact\"]";
        case SEEK_FORWARD   -> "\"seek\", 1, \"exact\"]";
        case SEEK_START_SUB -> "\"sub-seek\", 0]";
      }
      + "}\n";
  }
}
