package io.github.kamitejp.server;

import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.server.outmessage.OutMessage;

public sealed interface ServerEvent
  permits ServerEvent.Started,
          ServerEvent.ClientConnected,
          ServerEvent.CommandReceived,
          ServerEvent.MessageReceived,
          ServerEvent.AboutToSendMessage {
  record Started() implements ServerEvent {}
  record ClientConnected() implements ServerEvent {}
  record CommandReceived(IncomingCommand command) implements ServerEvent {}
  record MessageReceived(InMessage message) implements ServerEvent {}
  record AboutToSendMessage(OutMessage message) implements ServerEvent {}
}
