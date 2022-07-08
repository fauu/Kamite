package io.github.kamitejp.platform.linux.kde;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.CallbackHandler;

// DEV: Incomplete
public class KWinScreenshotAreaCallbackHandler implements CallbackHandler<String> {
  @Override
  public void handle(String result) {
    // DEV
    System.out.println("KWinScreenshotSelectAreaCallbackHandler.handle: " + result);
  }

  @Override
  public void handleError(DBusExecutionException e) {
    // DEV
    System.out.println("KWinScreenshotSelectAreaCallbackHandler.handleError: " + e);
  }
}
