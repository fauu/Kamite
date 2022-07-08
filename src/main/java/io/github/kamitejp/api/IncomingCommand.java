package io.github.kamitejp.api;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface IncomingCommand
  permits IncomingCommand.CombinedJSON,
          IncomingCommand.Segmented {
  record CombinedJSON(JsonNode root) implements IncomingCommand {}
  record Segmented(Kind kind, Params params) implements IncomingCommand {}

  sealed interface Kind
    permits IncomingCommand.Kind.Joined,
            IncomingCommand.Kind.Segmented {
    record Joined(String kind) implements Kind {}
    record Segmented(String group, String name) implements Kind {}
  }

  sealed interface Params
    permits IncomingCommand.Params.RawJSON,
            IncomingCommand.Params.JSONNode {
    record RawJSON(String paramsJSON) implements Params {}
    record JSONNode(JsonNode paramsNode) implements Params {}
  }
}
