package io.github.kamitejp.platform.linux.gnome;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

import io.github.kamitejp.dbus.Tuple2;
import io.github.kamitejp.dbus.Tuple4;

@SuppressWarnings("PMD.MethodNamingConventions")
@DBusInterfaceName("org.gnome.Shell.Screenshot")
public interface GnomeScreenshotDBusInterface extends DBusInterface {
  Tuple2<Boolean, String> ScreenshotArea(
    Integer x,
    Integer y,
    Integer width,
    Integer height,
    Boolean flash,
    String filename
  );

  Tuple4<Integer, Integer, Integer, Integer> SelectArea();
}
