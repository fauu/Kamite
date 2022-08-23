package io.github.kamitejp.platform.windows;

import java.awt.MouseInfo;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinGDI;
import com.tulskiy.keymaster.common.Provider;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.operations.PointSelectionMode;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.util.Result;

@SuppressWarnings("PMD") // DEV
public class WindowsPlatform extends GenericPlatform implements Platform, GlobalKeybindingProvider {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final DirectColorModel SCREENSHOT_COLOR_MODEL =
    new DirectColorModel(24, 0x00FF0000, 0xFF00, 0xFF);
  private static final int[] SCREENSHOT_BAND_MASKS = {
    SCREENSHOT_COLOR_MODEL.getRedMask(),
    SCREENSHOT_COLOR_MODEL.getGreenMask(),
    SCREENSHOT_COLOR_MODEL.getBlueMask()
  };

  private ScreenSelector selector;
  private Provider keymasterProvider;

  public WindowsPlatform() throws PlatformCreationException {
    super("win");
    if (getOS() != OS.WINDOWS) {
      throw new PlatformCreationException("Detected OS is not Windows");
    }
  }

  @Override
  public Optional<Path> getDefaultPipxVenvPythonPath(String venvName) {
    return getUserHomeDirPath().map(home ->
      home.resolve(".local/pipx/venvs").resolve(venvName).resolve("Scripts/python.exe")
    );
  }

  @Override
  public Result<Point, RecognitionOpError> getUserSelectedPoint(PointSelectionMode mode) {
    return switch (mode) {
      case INSTANT -> Result.Ok(Point.from(MouseInfo.getPointerInfo().getLocation()));
      case SELECT -> {
        if (selector == null) {
          selector = new ScreenSelector();
        }
        yield selector.getFuturePoint().join()
          .<Result<Point, RecognitionOpError>>map(p -> Result.Ok(p))
          .orElseGet(() -> Result.Err(RecognitionOpError.SELECTION_CANCELLED));
      }
    };
  }

  @Override
  public Result<Rectangle, RecognitionOpError> getUserSelectedArea() {
    if (selector == null) {
      selector = new ScreenSelector();
    }
    return selector.getFutureArea().join()
      .<Result<Rectangle, RecognitionOpError>>map(a -> Result.Ok(a))
      .orElseGet(() -> Result.Err(RecognitionOpError.SELECTION_CANCELLED));
  }

  @Override
  public Result<BufferedImage, RecognitionOpError> takeAreaScreenshot(Rectangle area) {
    final var USER = User32.INSTANCE;
    final var GDI = GDI32.INSTANCE;

    var srcDC = USER.GetDC(null);
    if (srcDC == null) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }
    var tgtBitmap = GDI.CreateCompatibleBitmap(srcDC, area.getWidth(), area.getHeight());
    if (tgtBitmap == null) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }
    var tgtDC = GDI.CreateCompatibleDC(srcDC);
    if (tgtDC == null) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }
    GDI.SelectObject(tgtDC, tgtBitmap);
    var success = GDI.BitBlt(
      tgtDC,
      0, 0,
      area.getWidth(), area.getHeight(),
      srcDC,
      area.getLeft(), area.getTop(),
      GDI32.SRCCOPY
    );
    if (!success) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    var bmi = new WinGDI.BITMAPINFO();
    bmi.bmiHeader.biWidth = area.getWidth();
    bmi.bmiHeader.biHeight = -area.getHeight();
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

    var numPixels = area.getWidth() * area.getHeight();

    var buffer = new Memory(numPixels * 4);
    var res = GDI.GetDIBits(
      srcDC, tgtBitmap, 0, area.getHeight(), buffer, bmi, WinGDI.DIB_RGB_COLORS
    );
    if (res == 0) {
      return Result.Err(RecognitionOpError.SCREENSHOT_FAILED);
    }

    var dataBuffer = new DataBufferInt(buffer.getIntArray(0, numPixels), numPixels);
    var raster = Raster.createPackedRaster(
      dataBuffer, area.getWidth(), area.getHeight(), area.getWidth(), SCREENSHOT_BAND_MASKS, null
    );
    var img = new BufferedImage(SCREENSHOT_COLOR_MODEL, raster, false, null);

    GDI.DeleteObject(tgtDC);
    GDI.DeleteObject(tgtBitmap);

    return Result.Ok(img);
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
