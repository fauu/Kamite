package io.github.kamitejp.chunk;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.config.Config;

public final class ChunkTransformer {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final List<CompiledTransform> transforms;

  private record CompiledTransform(Pattern pattern, String replacement) {}

  public ChunkTransformer(List<Config.Chunk.Transform> transforms) {
    this.transforms = transforms.stream()
      .map(t -> {
        if (t.replace().isEmpty()) {
          LOG.warn("Encountered empty chunk transform pattern");
          return null;
        }
        try {
          var pattern = Pattern.compile(t.replace());
          return new CompiledTransform(pattern, t.with());
        } catch (PatternSyntaxException e) {
          LOG.warn("Invalid chunk transform pattern", e);
        }
        return null;
      })
      .filter(Objects::nonNull)
      .toList();
    LOG.info("Initialized chunk transformer");
  }

  public String execute(String chunk) {
    String prevChunk = null;
    for (var t : transforms) {
      if (LOG.isDebugEnabled()) {
        prevChunk = chunk;
      }
      chunk = t.pattern().matcher(chunk).replaceAll(t.replacement());
      if (LOG.isDebugEnabled() && !chunk.equals(prevChunk)) {
        LOG.debug("Replaced `{}` with `{}`", t.pattern(), t.replacement());
        prevChunk = chunk;
      }
    }
    return chunk;
  }
}
