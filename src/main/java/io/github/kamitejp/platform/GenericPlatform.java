package io.github.kamitejp.platform;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.Result;

public abstract class GenericPlatform {
  protected static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected static final String LIB_DIR_PATH_RELATIVE = "lib/";
  protected static final String BIN_DIR_PATH_RELATIVE = "bin/";
  protected static final String GENERIC_PLATFORM_DIR_NAME = "generic";
  protected static final String APP_DIR_PATH_RELATIVE = "kamite/";
  protected static final String MANGAOCR_ADAPTER_FILENAME = "mangaocr_adapter.py";

  // Where the Kamite jar could be found relative to the root directory both in development and in
  // release
  private static final List<String> JAR_DIR_PATH_RELATIVE_VARIANTS =
    List.of("target/java", "lib/generic", "lib\\generic");

  private String binName;
  private OS os;
  private Path programPath;

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

  public final OS getOS() {
    if (os == null) {
      os = detectOS();
    }
    return os;
  }

  public static OS detectOS() {
    OS os = null;
    var osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
    if (osName.contains("win")) {
      os = OS.WINDOWS;
    } else if (osName.contains("lin")) {
      os = OS.LINUX;
    } else if (osName.contains("mac")) {
      os = OS.MACOS;
    } else {
      LOG.warn("OS detection failed. Assuming Linux");
      os = OS.LINUX;
    }
    return os;
  }

  public static Optional<BufferedImage> openImage(String path) {
    return openImage(new File(path));
  }

  public static Optional<BufferedImage> openImage(Path path) {
    return openImage(path.toFile());
  }

  @SuppressWarnings("WeakerAccess")
  public static Optional<BufferedImage> openImage(File file) {
    try {
      return Optional.ofNullable(ImageIO.read(file));
    } catch (IOException e) {
      LOG.error("Could not load image", e);
      return Optional.empty();
    }
  }

  @SuppressWarnings("unused")
  public static boolean writeImage(BufferedImage img, String path) {
    return writeImage(img, new File(path));
  }

  @SuppressWarnings("unused")
  public static boolean writeImage(BufferedImage img, Path path) {
    return writeImage(img, path.toFile());
  }

  @SuppressWarnings("WeakerAccess")
  public static boolean writeImage(BufferedImage img, File file) {
    try {
      ImageIO.write(img, "png", file);
    } catch (IOException e) {
      LOG.error("Could not save image", e);
      return false;
    }
    return true;
  }

  public Path getGenericLibDirPath() {
    return getProgramPath()
      .resolve(LIB_DIR_PATH_RELATIVE)
      .resolve(GENERIC_PLATFORM_DIR_NAME);
  }

  public Optional<Path> getUserHomeDirPath() {
    var propHome = System.getProperty("user.home");
    return propHome == null || propHome.isBlank()
      ? Optional.empty()
      : Optional.of(Paths.get(propHome));
  }

  public Path getMangaOCRAdapterPath() {
    return getGenericLibDirPath().resolve(MANGAOCR_ADAPTER_FILENAME);
  }

  private Path getProgramPath() {
    if (programPath == null) {
      try {
        programPath = determineProgramPath();
        LOG.debug("Program path determined to be: {}", programPath);
      } catch(URISyntaxException | IOException e) {
        throw new RuntimeException("Exception while determining program path", e);
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(
          "Exception while determining program path. If you are launching the program from a"
          + " network drive, try moving it to a local drive first.", e
        );
      }
    }
    return programPath;
  }

  protected static Result<Void, List<String>> checkIfDependenciesAvailable(
    List<SimpleDependency> dependencies
  ) {
    var unavailable = dependencies.stream()
      .filter(not(SimpleDependency::checkIsAvailable))
      .map(SimpleDependency::getName)
      .collect(toList());
    return unavailable.isEmpty() ? Result.Ok(null) : Result.Err(unavailable);
  }

  private static Path determineProgramPath() throws URISyntaxException, IOException {
    var classCodeLocationURI = GenericPlatform.class
      .getProtectionDomain()
      .getCodeSource()
      .getLocation()
      .toURI();
    var jarPathStr = new File(classCodeLocationURI).getCanonicalPath();
    var jarDirPathStr = jarPathStr.substring(0, jarPathStr.lastIndexOf(File.separator));
    // Strip relative JAR path so that we are left with the base program path
    for (var v : JAR_DIR_PATH_RELATIVE_VARIANTS) {
      if (jarDirPathStr.contains(v)) {
        jarDirPathStr = jarDirPathStr.replace(v, "");
        break;
      }
    }
    return Path.of(jarDirPathStr);
  }
}
