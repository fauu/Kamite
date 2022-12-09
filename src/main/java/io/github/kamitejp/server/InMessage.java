package io.github.kamitejp.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface InMessage
  permits InMessage.Command,
          InMessage.Request,
          InMessage.EventNotification {
  record Command(IncomingCommand incomingCommand) implements InMessage {}
  record Request(io.github.kamitejp.api.Request request) implements InMessage {}
  record EventNotification(io.github.kamitejp.server.EventNotification notification) // NOPMD - use of fully-qualified name necessary
    implements InMessage {}

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
      case "event-notification" -> {
        var notificatonParseRes = io.github.kamitejp.server.EventNotification.fromJSON(bodyNode); // NOPMD - use of fully-qualified name necessary
        if (notificatonParseRes.isErr()) {
          yield Result.Err("parsing event notification: %s".formatted(notificatonParseRes.err()));
        }
        yield Result.Ok(new EventNotification(notificatonParseRes.get()));
      }
      default -> Result.Err("message of unrecognized kind: %s".formatted(kind));
    };
  }
}
