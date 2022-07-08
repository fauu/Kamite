package io.github.kamitejp.platform.linux.kde;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

@DBusInterfaceName("org.kde.kwin.Screenshot")
public interface KWinScreenshotDBusInterface extends DBusInterface {
  String screenshotArea(Integer x, Integer y, Integer width, Integer height, Boolean includeCursor);
}
