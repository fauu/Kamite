package io.github.kamitejp.chunk;

import java.util.List;
import java.util.regex.Pattern;

public final class ChunkFilter {
  private static final List<String> REJECT_REGEXES =
    List.of(
      "^Textractor: ",
      "^Textractor x"
    );
  private static final List<Pattern> REJECT_PATTERNS;

  static {
    REJECT_PATTERNS = REJECT_REGEXES.stream()
      .map(r -> Pattern.compile(r))
      .toList();
  }

  private ChunkFilter() {}

  public static boolean shouldReject(String chunkStr) {
    for (var p : REJECT_PATTERNS) {
      if (p.matcher(chunkStr).find()) {
        return true;
      }
    }
    return false;
  }
}
