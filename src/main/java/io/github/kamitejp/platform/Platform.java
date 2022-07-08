package io.github.kamitejp.platform;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.Env;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractModel;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractResult;
import io.github.kamitejp.platform.linux.gnome.GnomePlatform;
import io.github.kamitejp.platform.linux.kde.PlasmaPlatform;
import io.github.kamitejp.platform.linux.wlroots.WlrootsPlatform;
import io.github.kamitejp.platform.linux.xorg.XorgPlatform;
import io.github.kamitejp.platform.windows.WindowsPlatform;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

public interface Platform {
  Logger LOG = LoggerFactory.getLogger(Platform.class);

  // This will determine the order in which the platforms are checked. If a
  // platform placed near the top has insufficient checks, it will cause
  // the wrong platform support to be initialized when in fact running on
  // one of the platforms placed below.
  List<Class<? extends Platform>> KNOWN_PLATFORMS =
    List.of(
      WindowsPlatform.class,
      GnomePlatform.class,
      PlasmaPlatform.class,
      WlrootsPlatform.class,
      XorgPlatform.class
    );

  List<Class<? extends Platform>> SUPPORTED_PLATFORMS =
    List.of(
      WlrootsPlatform.class,
      XorgPlatform.class
    );

  void init() throws PlatformInitializationException;

  String getName();

  OSFamily getOSFamily();

  void initOCR(OCREngine engine) throws PlatformOCRInitializationException;

  TesseractResult tesseractOCR(BufferedImage img, TesseractModel model);

  Path getMangaOCRWrapperPath();

  Result<Point, RecognitionOpError> getUserSelectedPoint();

  Result<Rectangle, RecognitionOpError> getUserSelectedArea();

  Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area);

  void openURL(String url);

  Optional<Path> getConfigDirPath();

  void destroy();

  default String getPlatformSpecificDirName(CPUArchitecture forCPUArch) {
    return "%s-%s".formatted(getName(), forCPUArch == null ? "generic" : forCPUArch);
  }

  static Platform createSuitable() {
    Platform platform = null;

    for (var tentativePlatform : KNOWN_PLATFORMS) {
      try {
        platform = tentativePlatform.getDeclaredConstructor().newInstance();
        break;
      } catch (InvocationTargetException e) {
        LOG.debug(
          "Could not create platform {}. Cause: {}",
          tentativePlatform.getSimpleName(),
          "" + e.getCause()
        );
      } catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
        LOG.error(
          "Internal error when instatiating Platform object."
          + " See stderr for stack trace"
        );
        e.printStackTrace();
        return null;
      }
    }

    if (platform == null) {
      LOG.error("Could not find a suitable platform");
      return null;
    }

    if (!SUPPORTED_PLATFORMS.contains(platform.getClass())) {
      if (Env.isDevMode()) {
        LOG.info(
          "Allowing unsupported platform {} because developer mode is enabled",
          platform.getClass().getSimpleName()
        );
      } else {
        LOG.error("Platform {} is not supported yet", platform.getClass().getSimpleName());
        return null;
      }
    }

    return platform;
  }
}
