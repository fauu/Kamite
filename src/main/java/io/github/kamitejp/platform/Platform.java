package io.github.kamitejp.platform;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.Env;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.linux.gnome.GnomePlatform;
import io.github.kamitejp.platform.linux.kde.PlasmaPlatform;
import io.github.kamitejp.platform.linux.wlroots.WlrootsPlatform;
import io.github.kamitejp.platform.linux.xorg.XorgPlatform;
import io.github.kamitejp.platform.macos.MacOSPlatform;
import io.github.kamitejp.platform.windows.WindowsPlatform;
import io.github.kamitejp.recognition.PointSelectionMode;
import io.github.kamitejp.recognition.RecognitionOpError;
import io.github.kamitejp.util.Result;

public interface Platform {
  Logger LOG = LogManager.getLogger(Platform.class);

  // This determines the order in which the platforms are checked
  List<Class<? extends Platform>> KNOWN_PLATFORMS =
    List.of(
      WindowsPlatform.class,
      MacOSPlatform.class,
      GnomePlatform.class,
      PlasmaPlatform.class,
      WlrootsPlatform.class,
      XorgPlatform.class
    );

  List<Class<? extends Platform>> UNSUPPORTED_PLATFORMS = List.of(MacOSPlatform.class);

  void init() throws PlatformInitializationException;

  String getName();

  OS getOS();

  default List<PlatformDependentFeature> getUnsupportedFeatures() {
    return List.of();
  }

  default boolean supports(PlatformDependentFeature feature) {
    return !getUnsupportedFeatures().contains(feature);
  }

  void initOCR() throws PlatformOCRInitializationException;

  Path getGenericLibDirPath();

  Optional<Path> getDefaultPipxVenvPythonPath(String venvName);

  Path getMangaOCRAdapterPath();

  Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode mode);

  Result<Rectangle, RecognitionOpError> getUserSelectedArea();

  Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area);

  void openURL(String url);

  Optional<Path> getUserHomeDirPath();

  Optional<Path> getConfigDirPath();

  Optional<Path> getDataDirPath();

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
          "Rejected platform {}. Cause: {}",
          tentativePlatform.getSimpleName(),
          "" + e.getCause()
        );
      } catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
        return null;
      }
    }

    if (platform == null) {
      LOG.error("Could not find a suitable platform");
      return null;
    }

    if (UNSUPPORTED_PLATFORMS.contains(platform.getClass())) {
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
