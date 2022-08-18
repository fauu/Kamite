package io.github.kamitejp.platform.linux.gnome;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.operations.PointSelectionMode;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.WaylandPlatform;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD")
public class GnomePlatform extends WaylandPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String TMP_SCREENSHOT_PATH = "/tmp/kamite-screenshot.png";

  private GnomeScreenshotDBusInterface screenshotDBusObject;

  public GnomePlatform() throws PlatformCreationException {
    if (!"GNOME".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))) {
      throw new PlatformCreationException("XDG_CURRENT_DESKTOP does not match 'GNOME'");
    }
  }

  @Override
  public List<PlatformDependentFeature> getUnsupportedFeatures() {
    return List.of(
      PlatformDependentFeature.GLOBAL_OCR,
      PlatformDependentFeature.GLOBAL_KEYBINDINGS
    );
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
  public Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode _mode) {
    var areaRes = getUserSelectedArea();
    if (areaRes.isErr()) {
      return Result.Err(areaRes.err());
    }
    return Result.Ok(areaRes.get().getCenter());
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    var futureArea = new CompletableFuture<Rectangle>();
    dbusClient.callWithCallback(
      screenshotDBusObject,
      "SelectArea",
      new GnomeScreenshotSelectAreaCallbackHandler(futureArea)
    );

    Rectangle area = null;
    try {
      area = futureArea.join();
    } catch (CompletionException e) {
      LOG.debug("GNOME screenshot API call failed", e.getCause()); // NOPMD
      return Result.Err(RecognitionOpError.SELECTION_FAILED);
    }

    return Result.Ok(area);
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    var futureRes = new CompletableFuture<GnomeScreenshotScreenshotAreaResult>();
    dbusClient.callWithCallback(
      screenshotDBusObject,
      "ScreenshotArea",
      new GnomeScreenshotScreenshotAreaCallbackHandler(futureRes),
      new Object[] {
        area.getLeft(),
        area.getTop(),
        area.getWidth(),
        area.getHeight(),
        /* flash */ true,
        TMP_SCREENSHOT_PATH
      }
    );

    GnomeScreenshotScreenshotAreaResult res = null;
    try {
      res = futureRes.join();
      if (res.success() == false) {
        LOG.debug("GNOME screenshot API reported lack of success taking area screenshot");
        return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
      }
    } catch (CompletionException e) {
      LOG.debug("GNOME screenshot API call failed", e); // NOPMD
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    var img = GenericPlatform.openImage(res.filenameUsed());
    if (img.isEmpty()) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    try {
      Files.delete(Paths.get(res.filenameUsed()));
    } catch (IOException e) {
      LOG.debug("Failed to delete temporary screenshot '{}': {}", res.filenameUsed(), e); // NOPMD
    }

    return Result.Ok(img.get());
  }
}
