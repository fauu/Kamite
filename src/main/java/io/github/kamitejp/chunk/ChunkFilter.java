package io.github.kamitejp.chunk;

import static java.util.function.Predicate.not;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ChunkFilter {
  protected static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private List<Pattern> rejectPatterns;

  public ChunkFilter(List<String> rawRejectPatterns) {
    rejectPatterns = rawRejectPatterns.stream()
      .filter(not(String::isEmpty))
      .map(p -> {
        try {
          return Pattern.compile(p);
        } catch (PatternSyntaxException e) {
          LOG.warn("Invalid chunk filter pattern", e);
        }
        return null;
      })
      .filter(Objects::nonNull)
      .toList();
  }

  public boolean shouldReject(String chunkStr) {
    for (var p : rejectPatterns) {
      if (p.matcher(chunkStr).find()) {
        return true;
      }
    }
    return false;
  }
}
