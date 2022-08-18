package io.github.kamitejp.platform.mpv;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public sealed interface MPVMessage
  permits MPVMessage.PropertyChange,
          MPVMessage.EndFile,
          MPVMessage.Unrecognized {
  // NOTE: '[\\s\\S]' is needed for data instead of '.' because of some Windows encoding weirdiness
  Pattern PROPERTY_CHANGE_RE = Pattern.compile("name\":\"(.+?)\"(?:,\"data\":([\\s\\S]+?))?\\}$");

  record PropertyChange(String name, String value) implements MPVMessage {}
  record EndFile() implements MPVMessage {}
  record Unrecognized() implements MPVMessage {}

  static MPVMessage parse(String msgJSON) {
    if (msgJSON.contains("event\":\"property-change\"")) {
      var m = PROPERTY_CHANGE_RE.matcher(msgJSON);
      m.find();
      var name = m.group(1);
      var value = m.groupCount() == 2 ? m.group(2) : null;
      return new PropertyChange(name, value);
    } else if (msgJSON.contains("\"end-file\"")) {
      return new EndFile();
    }
    return new Unrecognized();
  }

  static List<MPVMessage> parseMulti(String msgsJSON) {
    return Arrays.stream(msgsJSON.split("\n")).map(MPVMessage::parse).collect(toList());
  }
}
