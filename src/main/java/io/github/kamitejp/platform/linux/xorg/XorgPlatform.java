package io.github.kamitejp.platform.linux.xorg;

import java.awt.MouseInfo;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tulskiy.keymaster.common.Provider;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.RobotScreenshoter;
import io.github.kamitejp.platform.RobotScreenshoterUnavailableException;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.platform.linux.xorg.dependencies.slop.Slop;
import io.github.kamitejp.platform.linux.xorg.dependencies.slop.SlopResult;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

public class XorgPlatform extends LinuxPlatform implements GlobalKeybindingProvider {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
  public Result<Point, RecognitionOpError> getUserSelectedPoint() {
    return Result.Ok(Point.from(MouseInfo.getPointerInfo().getLocation()));
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    if (slop == null) {
      return Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    }

    return switch (slop.getAreaSelectionFromUser()) {
      case SlopResult.ExecutionFailed __ -> {
        LOG.error("slop did not execute properly");
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.Error error -> {
        LOG.error("slop returned an error: {}", error.error()); // NOPMD
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.Cancelled __ -> Result.Err(RecognitionOpError.SELECTION_CANCELLED);

      case SlopResult.FormatDifferentFromExpected expected -> {
        LOG.error( // NOPMD
          "slop returned malformatted result instead of expected {}",
          expected.expected()
        );
        yield Result.Err(RecognitionOpError.SELECTION_FAILED);
      }

      case SlopResult.ZeroArea __ -> {
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
}
