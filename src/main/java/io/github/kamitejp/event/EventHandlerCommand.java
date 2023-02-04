package io.github.kamitejp.event;

import io.github.kamitejp.config.Config;

record EventHandlerCommand(String kind, String paramsJSON) {
  static EventHandlerCommand fromConfigDefinition(Config.Events.Handler.Command command) {
    if (command == null) {
      return null;
    }
    return new EventHandlerCommand(command.kind(), command.params());
  }
}
