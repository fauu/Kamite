package io.github.kamitejp.platform.linux.kde;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.WaylandPlatform;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD")
public class PlasmaPlatform extends WaylandPlatform {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public PlasmaPlatform() throws PlatformCreationException {
    if (!"KDE".equalsIgnoreCase(getEnvVarAsNonNullableString("XDG_CURRENT_DESKTOP"))) {
      throw new PlatformCreationException("XDG_CURRENT_DESKTOP does not match 'KDE'");
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
}
