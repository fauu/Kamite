package io.github.kamitejp.platform.linux.gnome;

import java.util.concurrent.CompletableFuture;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.CallbackHandler;

import io.github.kamitejp.dbus.Tuple2;

// DEV: Incomplete
public class GnomeScreenshotAreaCallbackHandler
  implements CallbackHandler<Tuple2<Boolean, String>> {

  private CompletableFuture<GnomeScreenshotAreaResult> futureResult;

  public GnomeScreenshotAreaCallbackHandler(
    CompletableFuture<GnomeScreenshotAreaResult> futureResult
  ) {
    this.futureResult = futureResult;
  }

  @Override
  public void handle(Tuple2<Boolean, String> result) {
    futureResult.complete(new GnomeScreenshotAreaResult(result.a.booleanValue(), result.b));
  }

  @Override
  public void handleError(DBusExecutionException e) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
