package io.github.kamitejp.platform.linux.gnome;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD")
public class GnomePlatform extends LinuxPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private GnomeScreenshotDBusInterface screenshotDBusObject;

  public GnomePlatform() throws PlatformCreationException {
    if (!"GNOME".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))) {
      throw new PlatformCreationException("XDG_CURRENT_DESKTOP does not match GNOME");
    }
  }

  @Override
  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    super.initOCR(engine);

    if (dbusClient == null) {
      throw new PlatformOCRInitializationException.ScreenshotAPICommunicationFailure(
        "DBusClient is null"
      );
    }

    var maybeObj = dbusClient.getRemoteObject(
      "org.gnome.Shell",
      "/org/gnome/Shell/Screenshot",
      GnomeScreenshotDBusInterface.class
    );
    if (maybeObj.isEmpty()) {
      throw new PlatformOCRInitializationException.ScreenshotAPICommunicationFailure(
        "Could not get DBus GNOME screenshot object"
      );
    }
    screenshotDBusObject = maybeObj.get();
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    var futureArea = new CompletableFuture<Rectangle>();
    dbusClient.callWithCallback(
      screenshotDBusObject,
      "SelectArea",
      new GnomeScreenshotSelectAreaCallbackHandler(futureArea)
    );

    var area = futureArea.join();

    // DEV
    LOG.debug("Received area: {}", area);

    return Result.Ok(area);
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    var futureRes = new CompletableFuture<GnomeScreenshotAreaResult>();
    dbusClient.callWithCallback(
      screenshotDBusObject,
      "ScreenshotArea",
      new GnomeScreenshotAreaCallbackHandler(futureRes),
      new Object[] {
        area.getLeft(),
        area.getTop(),
        area.getWidth(),
        area.getHeight(),
        /* flash */ true,
        /* filename DEV */ "/tmp/kamite-screenshot.png"
      }
    );

    var res = futureRes.join();
    if (res.success() == false) {
      LOG.error("GNOME screenshot API reported lack of success taking area screenshot");
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    // DEV
    LOG.debug("Received screenshot filename: {}", res.filenameUsed());

    var img = GenericPlatform.openImage(res.filenameUsed());
    if (img.isEmpty()) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    return Result.Ok(img.get());
  }
}
