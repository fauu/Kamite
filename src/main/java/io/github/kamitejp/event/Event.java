package io.github.kamitejp.event;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface Event
  permits Event.ChunkAdd,
          Event.TabMouseenter,
          Event.TabMouseleave,
          Event.ApprootMouseenter,
          Event.ApprootMouseleave {
  Map<String, Class<? extends Event>> NAME_TO_CLASS = Map.of(
    "chunk-add", Event.ChunkAdd.class,
    "tab-mouseenter", Event.TabMouseenter.class,
    "tab-mouseleave", Event.TabMouseleave.class,
    "approot-mouseenter", Event.ApprootMouseenter.class,
    "approot-mouseleave", Event.ApprootMouseleave.class
  );

  record ChunkAdd(String chunkText) implements Event {}
  record TabMouseenter(EventDOMElement target, EventDOMElement relatedTarget) implements Event {}
  record TabMouseleave(EventDOMElement target, EventDOMElement relatedTarget) implements Event {}
  record ApprootMouseenter(EventDOMElement target, EventDOMElement relatedTarget)
    implements Event {}
  record ApprootMouseleave(EventDOMElement target, EventDOMElement relatedTarget)
    implements Event {}

  static Result<Event, String> fromJSON(JsonNode root) {
    try {
      var m = JSON.mapper();
      var dataNode = root.get("data");
      var event = switch (root.get("name").textValue()) {
        case "chunk-add" ->
          new Event.ChunkAdd(dataNode.get("chunkText").textValue());

        case "tab-mouseenter" ->
          new Event.TabMouseenter(
            m.treeToValue(dataNode.get("target"), EventDOMElement.class),
            m.treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          );

        case "tab-mouseleave" ->
          new Event.TabMouseleave(
            m.treeToValue(dataNode.get("target"), EventDOMElement.class),
            m.treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          );

        case "approot-mouseenter" ->
          new Event.ApprootMouseenter(
            m.treeToValue(dataNode.get("target"), EventDOMElement.class),
            m.treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          );

        case "approot-mouseleave" ->
          new Event.ApprootMouseleave(
            m.treeToValue(dataNode.get("target"), EventDOMElement.class),
            m.treeToValue(dataNode.get("relatedTarget"), EventDOMElement.class)
          );

        default -> null;
      };

      return event != null
        ? Result.Ok(event)
        : Result.Err("unknown event kind for event '%s'".formatted(root.toString()));
    } catch (JsonProcessingException e) {
      return Result.Err("parsing event data for event `%s`".formatted(root.toString()));
    }
  }
}
