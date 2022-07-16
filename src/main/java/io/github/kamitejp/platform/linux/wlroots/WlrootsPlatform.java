package io.github.kamitejp.platform.linux.wlroots;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.platform.linux.wlroots.dependencies.grim.Grim;
import io.github.kamitejp.platform.linux.wlroots.dependencies.grim.GrimResult;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.Slurp;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.SlurpMode;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.SlurpResult;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

public class WlrootsPlatform extends LinuxPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Slurp slurp;
  private Grim grim;

  public WlrootsPlatform() throws PlatformCreationException {
    if (getEnvVarAsNonNullableString("WAYLAND_DISPLAY").isEmpty()) {
      throw new PlatformCreationException("WAYLAND_DISPLAY is not set");
    }
  }

  @Override
  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    super.initOCR(engine);

    slurp = new Slurp();
    grim = new Grim();
    var checkRes = checkIfDependenciesAvailable(List.of(slurp, grim));
    if (checkRes.isErr()) {
      slurp = null;
      grim = null;
      throw new PlatformOCRInitializationException.MissingDependencies(checkRes.err());
    }
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint() {
    var slurpRunRes = runSlurp(SlurpMode.POINT);
    if (slurpRunRes.isErr()) {
      return Result.Err(slurpRunRes.err());
    }
    return switch (slurpRunRes.get()) {
      case SlurpResult.Point point -> Result.Ok(point.point());
      default -> {
        LOG.error("Received a valid but unexpected slurp result");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }
    };
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    var slurpRunRes = runSlurp(SlurpMode.AREA);
    if (slurpRunRes.isErr()) {
      return Result.Err(slurpRunRes.err());
    }
    return switch (slurpRunRes.get()) {
      case SlurpResult.Area area -> Result.Ok(area.area());
      default -> {
        LOG.error("Received a valid but unexpected slurp result");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }
    };
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    return runGrimWithArea(area);
  }

  private Result<SlurpResult, RecognitionOpError> runSlurp(SlurpMode mode) {
    if (slurp == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    var slurpRes = slurp.getSelectionFromUser(mode);
    return switch (slurpRes) {
      case SlurpResult.ExecutionFailed __ -> {
        LOG.error("slurp did not execute properly");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlurpResult.Error error -> {
        LOG.error("slurp returned an error: {}", error.error()); // NOPMD
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlurpResult.Cancelled __ -> Result.Err(RecognitionOpError.SELECTION_CANCELLED);

      case SlurpResult.FormatDifferentFromExpected expected -> {
        LOG.error( // NOPMD
          "slurp returned malformatted result instead of expected {}",
          expected.expected()
        );
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      default -> Result.Ok(slurpRes);
    };
  }

  private Result<BufferedImage, RecognitionOpError> runGrimWithArea(Rectangle a) {
    if (grim == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }
    var selection = "%d,%d %dx%d".formatted(a.getLeft(), a.getTop(), a.getWidth(), a.getHeight());
    return switch (grim.takeScreenshotOfSlurpSelection(selection)) {
      case GrimResult.ExecutionFailed __ -> {
        LOG.error("grim did not execute properly");
        yield Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
      }
      case GrimResult.Error error -> {
        LOG.error("grim returned an error: {}", error.error()); // NOPMD
        yield Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
      }
      case GrimResult.Screenshot screenshot -> Result.Ok(screenshot.screenshot());
    };
  }
}
