package io.github.kamitejp.platform.windows;

import java.awt.MouseInfo;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tulskiy.keymaster.common.Provider;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.RobotScreenshoter;
import io.github.kamitejp.platform.RobotScreenshoterUnavailableException;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD") // DEV
public class WindowsPlatform extends GenericPlatform implements Platform, GlobalKeybindingProvider {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private AreaSelectorFrame selector;
  private final RobotScreenshoter robotScreenshoter;
  private Provider keymasterProvider;

  public WindowsPlatform() throws PlatformCreationException {
    super("win");

    if (getOS() != OS.WINDOWS) {
      throw new PlatformCreationException("Detected OS is not Windows");
    }

    try {
      robotScreenshoter = new RobotScreenshoter();
    } catch (RobotScreenshoterUnavailableException e) {
      throw new PlatformCreationException("Could not initialize Robot Screenshoter", e);
    }
  }

  @Override
  public Optional<Path> getDefaultPipxVenvPythonPath(String venvName) {
    return getUserHomeDirPath().map(home ->
      home.resolve(".local/pipx/venvs").resolve(venvName).resolve("Scripts/python.exe")
    );
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint() {
    return Result.Ok(Point.from(MouseInfo.getPointerInfo().getLocation()));
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    selector = new AreaSelectorFrame();
    selector.setVisible(true);

    var maybeArea = selector.getFutureArea().join();
    selector.setVisible(false);

    return maybeArea
      .<Result<Rectangle, RecognitionOpError>>map(a -> Result.Ok(a))
      .orElseGet(() -> Result.Err(RecognitionOpError.SELECTION_CANCELLED));
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    var maybeScreenshot = robotScreenshoter.takeScreenshotOfArea(area.toAWT());
    if (maybeScreenshot.isEmpty()) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    // XXX: (DEV)
    // GenericPlatform.writeImage(maybeScreenshot.get(), "C:/dev/kamite-test.png");

    return Result.Ok(maybeScreenshot.get());
  }

  @Override
  public void openURL(String url) {
    try {
      Runtime.getRuntime().exec(new String[] { "rundll32", "url.dll,FileProtocolHandler", url });
    } catch (IOException e) {
      LOG.warn("Could not open web browser", e);
    }
  }

  @Override
  public Optional<Path> getConfigDirPath() {
    var envLocalAppData = getEnvVarAsNonNullableString("APPDATA");
    if (!envLocalAppData.isBlank()) {
      return Optional.of(Paths.get(envLocalAppData).resolve(CONFIG_DIR_PATH_RELATIVE));
    }
    return Optional.empty();
  }

  @Override
  public void destroy() {
    destroyKeybindings();
  }

  @Override
  public Provider getKeymasterProvider() {
    return keymasterProvider;
  }

  @Override
  public void setKeymasterProvider(Provider provider) {
    this.keymasterProvider = provider;
  }
}
