package io.github.kamitejp.api;

public sealed interface IncomingCommand
    permits IncomingCommand.CombinedJson,
            IncomingCommand.Segmented {
  record CombinedJson(com.fasterxml.jackson.databind.JsonNode root) implements IncomingCommand {}
  record Segmented(Kind kind, Params params) implements IncomingCommand {}

  sealed interface Kind
      permits IncomingCommand.Kind.Joined,
              IncomingCommand.Kind.Segmented {
    record Joined(String kind) implements Kind {}
    record Segmented(String group, String name) implements Kind {}
  }

  sealed interface Params
      permits IncomingCommand.Params.RawJson,
              IncomingCommand.Params.JsonNode {
    record RawJson(String paramsJson) implements Params {}
    record JsonNode(com.fasterxml.jackson.databind.JsonNode paramsNode) implements Params {}
  }
}
