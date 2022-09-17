package io.github.kamitejp.server;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.util.Result;

public sealed interface Notification
  permits Notification.ChunkAdded {

  record ChunkAdded(String chunk) implements Notification {}

  static Result<Notification, String> fromJSON(JsonNode root) {
    var kind = root.get("kind").textValue();
    var bodyNode = root.get("body");
    return switch (kind) {
      case "chunk-added" ->
        Result.Ok(new Notification.ChunkAdded(bodyNode.get("chunk").textValue()));
      default ->
        Result.Err("parsing notification body for notification '%s'".formatted(root.toString()));
    };
  }
}
