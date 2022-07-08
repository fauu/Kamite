package io.github.kamitejp.dbus;

import org.freedesktop.dbus.interfaces.DBusInterface;

public interface Receiver extends DBusInterface {
  void command(String kind, String paramsJSON);

  void command(String kind);
}
