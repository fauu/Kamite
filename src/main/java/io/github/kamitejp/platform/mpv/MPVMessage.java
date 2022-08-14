package io.github.kamitejp.platform.mpv;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;

public enum MPVMessage {
  PAUSED,
  UNPAUSED,
  END_FILE,
  KAMITE_SCRIPT_LOADED,
  UNRECOGNIZED;

  public static MPVMessage fromJSON(String msgJSON) {
    if (msgJSON.contains("\"name\":\"pause\"")) {
      if (msgJSON.contains("\"data\":true")) {
        return PAUSED;
      } else {
        return UNPAUSED;
      }
    } else if (msgJSON.contains("\"end-file\"")) {
      return END_FILE;
    } else if (msgJSON.contains("\"client_id\"")) {
      return KAMITE_SCRIPT_LOADED;
    }
    return UNRECOGNIZED;
  }

  public static List<MPVMessage> manyFromJSON(String msgsJSON) {
    return Arrays.stream(msgsJSON.split("\n")).map(MPVMessage::fromJSON).collect(toList());
  }
}
