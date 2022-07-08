package io.github.kamitejp.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface InMessage {
  record Command(IncomingCommand incomingCommand) implements InMessage {}
  record Request(io.github.kamitejp.api.Request request) implements InMessage {}

  static Result<InMessage, String> fromJSON(String json) {
    JsonNode root = null;
    try {
      root = JSON.mapper().readTree(json);
    } catch (JsonProcessingException e) {
      return Result.Err("parsing message JSON");
    }

    var bodyNode = root.get("body");
    var kind = root.get("kind").textValue();
    return switch (kind) {
      case "command" -> Result.Ok(new Command(new IncomingCommand.CombinedJSON(bodyNode)));
      case "request" -> {
        var requestParseRes = io.github.kamitejp.api.Request.fromJSON(bodyNode);
        if (requestParseRes.isErr()) {
          yield Result.Err("parsing request: %s".formatted(requestParseRes.err()));
        }
        yield Result.Ok(new Request(requestParseRes.get()));
      }
      default -> Result.Err("message of unrecognized kind: %s".formatted(kind));
    };
  }
}
