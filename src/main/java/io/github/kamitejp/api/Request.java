package io.github.kamitejp.api;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.chunk.ChunkEnhancement;
import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public record Request(long timestamp, Body body) {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public sealed interface Body permits Body.GetChunkEnhancements {
    record GetChunkEnhancements(String text, List<ChunkEnhancement> enhancements) implements Body {}
  }

  public static Result<Request, String> fromJSON(JsonNode root) {
    var kind = root.get("kind").textValue();
    var timestamp = root.get("timestamp").asLong();

    var bodyNode = root.get("body");
    var body = switch (kind) {
      case "get-chunk-enhancements" -> {
        try {
          var text = bodyNode.get("text").textValue();
          List<ChunkEnhancement> enhancements = JSON.mapper()
            .readerForListOf(ChunkEnhancement.class)
            .readValue(bodyNode.get("enhancements"));
          yield new Request.Body.GetChunkEnhancements(text, enhancements);
        } catch (IOException e) {
          LOG.debug("Exception while parsing requested chunk enhancements: {}", e);
          yield null;
        }
      }
      default -> null;
    };
    if (body == null) {
      return Result.Err("parsing request body for request '%s'".formatted(root.toString()));
    }

    return Result.Ok(new Request(timestamp, body));
  }
}
