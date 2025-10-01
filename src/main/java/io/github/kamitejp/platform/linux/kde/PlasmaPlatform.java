package io.github.kamitejp.platform.linux.kde;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;

import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.linux.dependencies.slurp.Slurp;
import io.github.kamitejp.platform.linux.kde.dependencies.spectacle.Spectacle;
import io.github.kamitejp.platform.linux.kde.dependencies.spectacle.SpectacleResult;
import io.github.kamitejp.platform.linux.dependencies.slurp.SlurpFade;
import io.github.kamitejp.platform.linux.dependencies.slurp.SlurpMode;
import io.github.kamitejp.platform.linux.dependencies.slurp.SlurpResult;
import io.github.kamitejp.recognition.OCREngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.linux.WaylandPlatform;
import io.github.kamitejp.recognition.PointSelectionMode;
import io.github.kamitejp.recognition.RecognitionOpError;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD")
public class PlasmaPlatform extends WaylandPlatform {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Spectacle spectacle;
  private Slurp slurp;

  public PlasmaPlatform() throws PlatformCreationException {
    if (!"KDE".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))) {
      throw new PlatformCreationException("XDG_CURRENT_DESKTOP does not match 'KDE'");
    }
  }

  @Override
  public List<PlatformDependentFeature> getUnsupportedFeatures() {
    return List.of(
      PlatformDependentFeature.GLOBAL_KEYBINDINGS
    );
  }

  @SuppressWarnings("OverlyBroadThrowsClause")
  @Override
  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    super.initOCR(engine);

    slurp = new Slurp();
    spectacle = new Spectacle();
    var checkRes = checkIfDependenciesAvailable(List.of(slurp, spectacle));
    if (checkRes.isErr()) {
        spectacle = null;
        throw new PlatformOCRInitializationException.MissingDependencies(checkRes.err());
    }
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode mode) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    var slurpRunRes = runSlurp(SlurpMode.AREA, SlurpFade.FADE);
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
    if (spectacle == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }
    return switch (spectacle.takeScreenshot(area)) {
      case SpectacleResult.ExecutionFailed _ -> {
        LOG.error("spectacle did not execute properly");
        yield Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
      }
      case SpectacleResult.Error error -> {
        LOG.error("spectacle returned an error: {}", error::error);
        yield Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
      }
      case SpectacleResult.Screenshot screenshot -> Result.Ok(screenshot.screenshot());
    };
  }

  private Result<SlurpResult, RecognitionOpError> runSlurp(SlurpMode mode, SlurpFade fade) {
    if (slurp == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    var slurpRes = slurp.getSelectionFromUser(mode, fade);
    return switch (slurpRes) {
      case SlurpResult.ExecutionFailed _ -> {
        LOG.error("slurp did not execute properly");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlurpResult.Error error -> {
        LOG.error("slurp returned an error: {}", error::error);
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlurpResult.Cancelled _ -> Result.Err(RecognitionOpError.SELECTION_CANCELLED);

      case SlurpResult.FormatDifferentFromExpected expected -> {
        LOG.error(
                "slurp returned malformatted result instead of expected {}",
                expected::expected
        );
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      default -> Result.Ok(slurpRes);
    };
  }
}
