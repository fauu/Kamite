package io.github.kamitejp.api;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.util.Result;

public record Request(long timestamp, Body body) {
  public sealed interface Body permits Body.AddFurigana {
    record AddFurigana(String text) implements Body {}
  }

  public static Result<Request, String> fromJSON(JsonNode root) {
    var kind = root.get("kind").textValue();
    var timestamp = root.get("timestamp").asLong();

    var bodyNode = root.get("body");
    var body = switch (kind) {
      case "add-furigana" -> new Request.Body.AddFurigana(bodyNode.get("text").textValue());
      default -> null;
      // default -> Result.Err("unhandled request kind for request '%s'".formatted(root.toString()));
    };
    if (body == null) {
      return Result.Err("parsing request body for request '%s'".formatted(root.toString()));
    }

    return Result.Ok(new Request(timestamp, body));
  }
}
