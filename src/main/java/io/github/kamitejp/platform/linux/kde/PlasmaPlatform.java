package io.github.kamitejp.platform.linux.kde;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD")
public class PlasmaPlatform extends LinuxPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public PlasmaPlatform() throws PlatformCreationException {
    if (!"KDE".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))) {
      throw new PlatformCreationException("XDG_CURRENT_DESKTOP does not match Plasma");
    }
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    throw new UnsupportedOperationException("Not implemented");
  }

  // if (this.dbusClient == null) {
  //   LOG.error("DBusClient is null");
  //   return BoxRecognitionResult.empty(
  //       BoxRecognitionResult.EmptyReason.SCREENSHOT_API_COMMUNICATION_FAILED);
  // }
  //
  // Optional<KWinScreenshotDBusInterface> maybeObj =
  //         this.dbusClient.getRemoteObject(
  //                 "org.kde.KWin",
  //                 "/Screenshot",
  //                 KWinScreenshotDBusInterface.class);
  // if (maybeObj.isEmpty()) {
  //   LOG.error("Could not get DBus remote object");
  //   return BoxRecognitionResult.empty(BoxRecognitionResult.EmptyReason.SCREENSHOT_API_COMMUNICATION_FAILED);
  // }
  //
  // this.dbusClient.callWithCallback(maybeObj.get(), "screenshotArea", new KWinScreenshotAreaCallbackHandler(), 50, 50, 200, 200, false);
}
