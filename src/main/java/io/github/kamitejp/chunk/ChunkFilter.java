package io.github.kamitejp.chunk;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ChunkFilter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private List<Pattern> rejectPatterns;

  public ChunkFilter(List<String> rawRejectPatterns) {
    rejectPatterns = rawRejectPatterns.stream()
      .map(p -> {
        if (p.isEmpty()) {
          return null;
        }
        try {
          return Pattern.compile(p);
        } catch (PatternSyntaxException e) {
          LOG.warn("Invalid chunk filter pattern", e);
        }
        return null;
      })
      .filter(Objects::nonNull)
      .toList();
    LOG.info("Initialized chunk filter");
  }

  public boolean shouldReject(String chunk) {
    for (var p : rejectPatterns) {
      if (p.matcher(chunk).find()) {
        return true;
      }
    }
    return false;
  }
}
