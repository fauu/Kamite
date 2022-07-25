package io.github.kamitejp.platform.mpv;

public enum MPVMessage {
  END_FILE,
  PAUSED,
  UNPAUSED,
  UNRECOGNIZED;

  public static MPVMessage fromJSON(String msgJSON) {
    if (msgJSON.contains("\"end-file\"")) {
      return END_FILE;
    } else if (msgJSON.contains("\"name\":\"pause\"")) {
      if (msgJSON.contains("\"data\":true")) {
        return PAUSED;
      } else {
        return UNPAUSED;
      }
    }
    return UNRECOGNIZED;
  }
}
