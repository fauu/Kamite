package io.github.kamitejp.platform.linux.gnome;

import java.util.concurrent.CompletableFuture;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.CallbackHandler;

import io.github.kamitejp.dbus.Tuple2;

public class GnomeScreenshotScreenshotAreaCallbackHandler
  implements CallbackHandler<Tuple2<Boolean, String>> {

  private CompletableFuture<GnomeScreenshotScreenshotAreaResult> futureResult;

  public GnomeScreenshotScreenshotAreaCallbackHandler(
    CompletableFuture<GnomeScreenshotScreenshotAreaResult> futureResult
  ) {
    this.futureResult = futureResult;
  }

  @Override
  public void handle(Tuple2<Boolean, String> result) {
    futureResult.complete(
      new GnomeScreenshotScreenshotAreaResult(result.a.booleanValue(), result.b)
    );
  }

  @Override
  public void handleError(DBusExecutionException e) {
    futureResult.completeExceptionally(e);
  }
}
