package io.github.kamitejp.platform.linux.xorg;

import java.awt.MouseInfo;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tulskiy.keymaster.common.Provider;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RobotScreenshoter;
import io.github.kamitejp.platform.RobotScreenshoterUnavailableException;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.platform.linux.xorg.dependencies.slop.Slop;
import io.github.kamitejp.platform.linux.xorg.dependencies.slop.SlopResult;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.PointSelectionMode;
import io.github.kamitejp.recognition.RecognitionOpError;
import io.github.kamitejp.util.Result;

public class XorgPlatform extends LinuxPlatform implements GlobalKeybindingProvider {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RobotScreenshoter robotScreenshoter;
  private Provider keymasterProvider;

  private Slop slop;

  public XorgPlatform() throws PlatformCreationException {
    try {
      robotScreenshoter = new RobotScreenshoter();
    } catch (RobotScreenshoterUnavailableException e) {
      throw new PlatformCreationException("Could not initialize Robot Screenshoter", e);
    }
  }

  @Override
  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    super.initOCR(engine);

    slop = new Slop();
    var res = checkIfDependenciesAvailable(List.of(slop));
    if (res.isErr()) {
      slop = null;
      throw new PlatformOCRInitializationException.MissingDependencies(res.err());
    }
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode mode) {
    return switch (mode) {
      case INSTANT -> Result.Ok(Point.from(MouseInfo.getPointerInfo().getLocation()));
      case SELECT -> {
        if (slop == null) {
          yield Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
        }

        var slopRunRes = runSlop();
        if (slopRunRes.isErr()) {
          yield Result.Err(slopRunRes.err());
        }

        yield Result.Ok(slopRunRes.get().getCenter());
      }
    };
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    if (slop == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    var slopRunRes = runSlop();
    if (slopRunRes.isErr()) {
      return Result.Err(slopRunRes.err());
    }

    return Result.Ok(slopRunRes.get());
  }

  private Result<Rectangle, RecognitionOpError> runSlop() {
    if (slop == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    var slopRes = slop.getAreaSelectionFromUser();
    return switch (slopRes) {
      case SlopResult.ExecutionFailed __ -> {
        LOG.error("slop did not execute properly");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.Error error -> {
        LOG.error("slop returned an error: {}", () -> error.error());
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.Cancelled ignored -> Result.Err(RecognitionOpError.SELECTION_CANCELLED);

      case SlopResult.FormatDifferentFromExpected expected -> {
        LOG.error(
          "slop returned malformatted result instead of expected {}",
          () -> expected.expected()
        );
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.ZeroArea ignored -> {
        LOG.error("slop returned a zero area");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.Area area -> Result.Ok(area.area());
    };
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    var maybeScreenshot = robotScreenshoter.takeScreenshotOfArea(area.toAWT());
    if (maybeScreenshot.isEmpty()) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }
    return Result.Ok(maybeScreenshot.get());
  }

  @Override
  public void destroy() {
    destroyKeybindings();
    super.destroy();
  }

  @Override
  public Provider getKeymasterProvider() {
    return this.keymasterProvider;
  }

  @Override
  public void setKeymasterProvider(Provider provider) {
    this.keymasterProvider = provider;
  }

  public XorgDesktop getDesktop() {
    return "GNOME".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))
      ? XorgDesktop.GNOME
      : XorgDesktop.UNKNOWN;
  }
}
