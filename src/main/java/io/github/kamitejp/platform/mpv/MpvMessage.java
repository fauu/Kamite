package io.github.kamitejp.platform.mpv;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public sealed interface MpvMessage
    permits MpvMessage.PropertyChange,
            MpvMessage.EndFile,
            MpvMessage.Unrecognized {
  // NOTE: '[\\s\\S]' is needed for data instead of '.' because of some Windows encoding weirdiness
  Pattern PROPERTY_CHANGE_RE = Pattern.compile("name\":\"(.+?)\"(?:,\"data\":([\\s\\S]+?))?\\}$");

  record PropertyChange(String name, String value) implements MpvMessage {}
  record EndFile() implements MpvMessage {}
  record Unrecognized() implements MpvMessage {}

  static MpvMessage parse(String msgJson) {
    if (msgJson.contains("event\":\"property-change\"")) {
      var m = PROPERTY_CHANGE_RE.matcher(msgJson);
      m.find();
      var name = m.group(1);
      var value = m.groupCount() == 2 ? m.group(2) : null;
      return new PropertyChange(name, value);
    } else if (msgJson.contains("\"end-file\"")) {
      return new EndFile();
    }
    return new Unrecognized();
  }

  static List<MpvMessage> parseMulti(String msgsJson) {
    return Arrays.stream(msgsJson.split("\n")).map(MpvMessage::parse).collect(toList());
  }
}
