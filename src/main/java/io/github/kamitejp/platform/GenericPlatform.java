package io.github.kamitejp.platform;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.platform.dependencies.tesseract.Tesseract;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractModel;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractResult;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.util.Result;

public abstract class GenericPlatform {
  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String MANGAOCR_USER_LAUNCHER_SCRIPT_FILENAME = "mangaocr.sh";

  protected static final String LIB_DIR_PATH_RELATIVE = "lib/";
  protected static final String BIN_DIR_PATH_RELATIVE = "bin/";
  protected static final String GENERIC_PLATFORM_DIR_NAME = "generic";
  protected static final String CONFIG_DIR_PATH_RELATIVE = "kamite/";

  // Where the Kamite jar could be found relative to the root directory both in development and in
  // release
  private static final List<String> JAR_DIR_PATH_RELATIVE_VARIANTS =
    List.of("target/java", "lib/generic");

  private String binName;
  private Path programPath;

  private Tesseract tesseract;

  protected GenericPlatform(String binName) {
    this.binName = binName;
  }

  public void init() throws PlatformInitializationException {
    // Empty: no generic init procedure needed
  }

  public static String getEnvVarAsNonNullableString(String name) {
    return Optional.ofNullable(System.getenv(name)).orElse("");
  }

  public String getName() {
    return binName;
  }

  public static Optional<OS> getOS() {
    OS os = null;
    var osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
    if (osName.contains("win")) {
      os = OS.WINDOWS;
    } else if (osName.contains("nux")) {
      os = OS.LINUX;
    }
    return Optional.ofNullable(os);
  }

  public static boolean isOS(OS expectedOS) {
    return getOS().filter(os -> os == expectedOS).isPresent();
  }

  public static Optional<BufferedImage> openImage(String path) {
    try {
      return Optional.of(ImageIO.read(new File(path)));
    } catch (IOException e) {
      LOG.error("Could not load image", e);
      return Optional.empty();
    }
  }

  public static boolean writeImage(BufferedImage img, String path) {
    try {
      ImageIO.write(img, "png", new File(path));
    } catch (IOException e) {
      LOG.error("Could not save image", e);
      return false;
    }
    return true;
  }

  public void initOCR(OCREngine engine) throws PlatformOCRInitializationException {
    if (engine instanceof OCREngine.Tesseract) {
      tesseract = new Tesseract();
      if (!tesseract.checkIsAvailable()) {
        throw new PlatformOCRInitializationException.MissingDependencies(tesseract.NAME);
      }
    }
  }

  public TesseractResult tesseractOCR(BufferedImage img, TesseractModel model) {
    return tesseract.ocr(img, model);
  }

  protected Path getProgramPath() {
    if (programPath == null) {
      try {
        programPath = determineProgramPath();
        LOG.debug("Program path determined to be: {}", programPath);
      } catch(URISyntaxException | IOException e) {
        throw new RuntimeException("Exception while determining program path", e);
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(
          "Exception while determining program path. If you are launching the program from a " +
          "network drive, try moving it to your local drive first.", e
        );
      }
    }
    return this.programPath;
  }

  protected Result<Void, List<String>> checkIfDependenciesAvailable(
    List<SimpleDependency> dependencies
  ) {
    var unavailable = dependencies.stream()
      .filter(not(SimpleDependency::checkIsAvailable))
      .map(d -> d.getName())
      .collect(toList());
    return unavailable.isEmpty() ? Result.Ok(null) : Result.Err(unavailable);
  }

  private Path determineProgramPath() throws URISyntaxException, IOException {
    var classCodeLocationURI = GenericPlatform.class
      .getProtectionDomain()
      .getCodeSource()
      .getLocation()
      .toURI();
    var jarPathStr = new File(classCodeLocationURI).getCanonicalPath();
    var jarDirPathStr = jarPathStr.substring(0, jarPathStr.lastIndexOf(File.separator));
    for (var v : JAR_DIR_PATH_RELATIVE_VARIANTS) {
      if (jarDirPathStr.contains(v)) {
        jarDirPathStr = jarDirPathStr.replace(v, "");
        break;
      }
    }
    return Path.of(jarDirPathStr);
  }
}
