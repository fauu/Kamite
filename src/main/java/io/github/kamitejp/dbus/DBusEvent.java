package io.github.kamitejp.dbus;

import io.github.kamitejp.api.IncomingCommand;

public sealed interface DBusEvent
  permits DBusEvent.CommandReceived {
  record CommandReceived(IncomingCommand command) implements DBusEvent {}
}
