package io.github.kamitejp.platform.linux.gnome;

import java.util.concurrent.CompletableFuture;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.CallbackHandler;

import io.github.kamitejp.dbus.Tuple4;
import io.github.kamitejp.geometry.Rectangle;

// DEV: Incomplete
public class GnomeScreenshotSelectAreaCallbackHandler
  implements CallbackHandler<Tuple4<Integer, Integer, Integer, Integer>> {

  private CompletableFuture<Rectangle> futureArea;

  public GnomeScreenshotSelectAreaCallbackHandler(CompletableFuture<Rectangle> futureArea) {
    this.futureArea = futureArea;
  }

  @Override
  public void handle(Tuple4<Integer, Integer, Integer, Integer> result) {
    futureArea.complete(Rectangle.ofStartAndDimensions(result.a, result.b, result.c, result.d));
  }

  @Override
  public void handleError(DBusExecutionException e) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
