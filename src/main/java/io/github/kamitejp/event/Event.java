package io.github.kamitejp.event;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface Event
  permits Event.ChunkAdd,
          Event.WindowMouseenter,
          Event.WindowMouseleave {
  Map<String, Class<? extends Event>> EVENT_NAME_TO_CLASS = Map.of(
    "chunk-add", Event.ChunkAdd.class,
    "window-mouseenter", Event.WindowMouseenter.class,
    "window-mouseleave", Event.WindowMouseleave.class
  );

  record ChunkAdd(String chunk) implements Event {}
  record WindowMouseenter(EventDOMElement target, EventDOMElement relatedTarget) implements Event {}
  record WindowMouseleave(EventDOMElement target, EventDOMElement relatedTarget) implements Event {}

  static Result<Event, String> fromJSON(JsonNode root) {
    var kind = root.get("kind").textValue();
    var dataNode = root.get("data");
    try {
      return switch (kind) {
        case "chunk-add" ->
          Result.Ok(new Event.ChunkAdd(dataNode.get("chunk").textValue()));
        case "window-mouseenter" ->
          Result.Ok(new Event.WindowMouseenter(
            JSON.mapper().treeToValue(dataNode.get("target"), EventDOMElement.class),
            JSON.mapper().treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          ));
        case "window-mouseleave" ->
          Result.Ok(new Event.WindowMouseleave(
            JSON.mapper().treeToValue(dataNode.get("target"), EventDOMElement.class),
            JSON.mapper().treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          ));
        default ->
          Result.Err("parsing event kind for event '%s'".formatted(root.toString()));
      };
    } catch (JsonProcessingException e) {
      return Result.Err("parsing event data for event `%s`".formatted(root.toString()));
    }
  }
}
