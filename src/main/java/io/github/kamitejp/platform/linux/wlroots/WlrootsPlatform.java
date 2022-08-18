package io.github.kamitejp.platform.linux.wlroots;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.operations.PointSelectionMode;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.WaylandPlatform;
import io.github.kamitejp.platform.linux.wlroots.dependencies.grim.Grim;
import io.github.kamitejp.platform.linux.wlroots.dependencies.grim.GrimResult;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.Slurp;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.SlurpFade;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.SlurpMode;
import io.github.kamitejp.platform.linux.wlroots.dependencies.slurp.SlurpResult;
import io.github.kamitejp.platform.linux.wlroots.dependencies.wlrctl.Wlrctl;
import io.github.kamitejp.platform.linux.wlroots.dependencies.wlrctl.WlrctlResult;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

public class WlrootsPlatform extends WaylandPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Needed so that it runs only after slurp selection prompt has been initialized
  private static final long WLRCTL_DELAY_MS = 66;

  private Slurp slurp;
  private Grim grim;
  private Wlrctl wlrctl;
  private Executor wlrctlDelayedExecutor;

  public WlrootsPlatform() throws PlatformCreationException {
    // Empty
  }

  @Override
  public List<PlatformDependentFeature> getUnsupportedFeatures() {
    return List.of(PlatformDependentFeature.GLOBAL_KEYBINDINGS);
  }

  @Override
  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    super.initOCR(engine);

    slurp = new Slurp();
    grim = new Grim();
    wlrctl = new Wlrctl();
    var checkRes = checkIfDependenciesAvailable(List.of(slurp, grim, wlrctl));
    if (checkRes.isErr()) {
      var unavailable = checkRes.err();
      if (unavailable.size() == 1 && "wlrctl".equalsIgnoreCase(unavailable.get(0))) {
        wlrctl = null;
        LOG.warn("wlrctl is missing, Auto Block Instant mode will not be available");
      } else {
        slurp = null;
        grim = null;
        wlrctl = null;
        throw new PlatformOCRInitializationException.MissingDependencies(checkRes.err());
      }
    } else {
      wlrctlDelayedExecutor =
        CompletableFuture.delayedExecutor(WLRCTL_DELAY_MS, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode mode) {
    CompletableFuture<Result<SlurpResult, RecognitionOpError>> futureSlurpRunRes = null;
    if (mode == PointSelectionMode.INSTANT) {
      if (wlrctl == null) {
        LOG.warn(
          "Requested Auto Block Instant mode but wlrctl is unavailable."
          + " Running with Select mode instead"
        );
      } else {
        futureSlurpRunRes = CompletableFuture.supplyAsync(
          () -> runSlurp(SlurpMode.POINT, SlurpFade.NO_FADE)
        );
        clickWithWlrctl();
      }
    }

    Result<SlurpResult, RecognitionOpError> slurpRunRes = null;
    if (futureSlurpRunRes != null) {
      try {
        slurpRunRes = futureSlurpRunRes.get();
      } catch (InterruptedException | ExecutionException e) {
        LOG.error("Could not run slurp. See stderr for the stack trace");
        e.printStackTrace();
      }
    } else {
      slurpRunRes = runSlurp(SlurpMode.POINT, SlurpFade.FADE);
    }
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
    return runGrimWithArea(area);
  }

  private Result<SlurpResult, RecognitionOpError> runSlurp(SlurpMode mode, SlurpFade fade) {
    if (slurp == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    var slurpRes = slurp.getSelectionFromUser(mode, fade);
    return switch (slurpRes) {
      case SlurpResult.ExecutionFailed ignored -> {
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

  private void clickWithWlrctl() {
    var futureWlrctlRes =
      CompletableFuture.supplyAsync(() -> wlrctl.mouseClick(), wlrctlDelayedExecutor);
    try {
      switch (futureWlrctlRes.get()) {
        case WlrctlResult.ExecutionFailed ignored -> {
          LOG.error("wlrctl did not execute properly");
        }
        case WlrctlResult.Error error -> {
          LOG.error("wlrctl returned an error: {}", error.error()); // NOPMD
        }
        default -> {}
      }
    } catch (InterruptedException | ExecutionException e) {
      LOG.error("Could not run wlrctl. See stderr for the stack trace");
      e.printStackTrace();
    }
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
