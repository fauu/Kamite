package io.github.kamitejp.platform;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.LocalOCRAdapter;
import io.github.kamitejp.recognition.LocalOCRError;
import io.github.kamitejp.recognition.OCRAdapterEvent;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;
import io.github.kamitejp.recognition.StatefulOCRAdapter;
import io.github.kamitejp.util.Result;

public class MangaOCRController
    extends StatefulOCRAdapter
    implements LocalOCRAdapter<OCRAdapterOCRParams.Empty> {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PIPX_DEFAULT_VENV_NAME = "manga-ocr";
  private static final int RECOGNITION_TIMEOUT_S = 8;

  // XXX
  // private final Consumer<MangaOCREvent> eventCb;
  private final String[] cmd;
  private Process process;
  private BufferedReader outputReader;

  public MangaOCRController(Platform platform, String customPythonPath) {
  // ) throws MangaOCRInitializationException {
    var pythonPath = effectivePythonPath(platform, customPythonPath);
    if (pythonPath == null) {
    // XXX
      // throw new MangaOCRInitializationException(
      //   "pipx \"Manga OCR\" installation absent at default location."
      //   + " Please specify `ocr.mangaocr.pythonPath` in the config"
      // );
    }

    cmd = new String[] { pythonPath, platform.getMangaOCRAdapterPath().toString() };
  }

  private String effectivePythonPath(Platform platform, String customPath) {
    if (customPath != null) {
      return customPath;
    }
    var maybeDefaultPath = platform.getDefaultPipxVenvPythonPath(PIPX_DEFAULT_VENV_NAME);
    if (maybeDefaultPath.isPresent()) {
      var defaultPath = maybeDefaultPath.get();
      if (Files.isExecutable(defaultPath)) {
        return defaultPath.toString();
      }
    }
    return null;
  }
  
  @Override
  public void doInit() {
    dispatchEvent(
      new OCRAdapterEvent.Launching("Starting using `{}`".formatted(cmd[0]))
    );
    var pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true); // Needed to catch the "Downloading" messages
    try {
      process = pb.start();
      outputReader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
      );
      dispatchEvent(new OCRAdapterEvent.Launched(null));

      var ready = false;
      var dispatchedExtraSetupEvent = false;
      for (var line = outputReader.readLine(); line != null; line = outputReader.readLine()) {
        LOG.debug("Received output line: {}", line);
        if (!dispatchedExtraSetupEvent && line.startsWith("Downloading")) {
          //noinspection ObjectAllocationInLoop
          dispatchEvent(
            new OCRAdapterEvent.StartedExtraSetup("Downloading base model. This might take a while")
          );
          dispatchedExtraSetupEvent = true;
        }
        if ("READY".equals(line)) {
          ready = true;
          break;
        }
      }
      if (!ready) {
        dispatchEvent(new OCRAdapterEvent.FailedFatally("Failed to report readiness"));
        return;
      }
    } catch (IOException e) {
      fatalFailure("Error while reading initial output: {}".formatted(e.getMessage()));
      return;
    }

    isReady = true;
    dispatchEvent(new OCRAdapterEvent.Initialized(null));
  }

  private final Supplier<String> outputLineSupplier = () -> {
    try {
      return outputReader.readLine();
    } catch (IOException e) {
      fatalFailure("Error while reading \"Manga OCR\" output. See stderr for stack trace");
      e.printStackTrace();
    }
    return null;
  };

  public Result<BoxRecognitionOutput, LocalOCRError> recognize(
    BufferedImage img,
    OCRAdapterOCRParams.Empty _params
  ) {
    if (!isReady) {
      throw new IllegalStateException("Attempted to use \"Manga OCR\" while it was not ready");
    }
    try {
      // Send image
      var imgBytes = ImageOps.encodeIntoByteArrayOutputStream(img).toByteArray();
      var encodedImgBytesSize = ByteBuffer.allocate(4).putInt(imgBytes.length).array();
      var out = process.getOutputStream();
      out.write(encodedImgBytesSize);
      out.flush();
      out.write(imgBytes);
      out.flush();

      // Read reply
      var futureLine = CompletableFuture.supplyAsync(outputLineSupplier);
      var line = futureLine.get(RECOGNITION_TIMEOUT_S, TimeUnit.SECONDS);
      if (line == null) {
        return Result.Err(new LocalOCRError.Other("\"Manga OCR\" did not reply with text on time"));
      }

      if (line.startsWith("Traceback (most")) {
        var errBuilder = new StringBuilder();
        String errLine = null;
        while ((errLine = outputReader.readLine()) != null) {
          errBuilder.append(errLine);
        }
        // XXX: Move the logging above and remove it from handleCrash()
        fatalFailure("\"Manga OCR\" responded with an error: %s".formatted(errBuilder.toString()));
        return Result.Err(new LocalOCRError.Other(
          "\"Manga OCR\" responded with an error: %s".formatted(errBuilder.toString())
        ));
      }

      return Result.Ok(BoxRecognitionOutput.fromString(line));
    } catch (IOException | InterruptedException | ExecutionException e) {
      fatalFailure("Error while communicating with \"Manga OCR\". See stderr for stack trace");
      e.printStackTrace();
      return Result.Err(new LocalOCRError.Other(
        "Error while communicating with \"Manga OCR\". See stderr for stack trace"
      ));
    } catch (TimeoutException e) {
      isReady = false;
      dispatchEvent(new OCRAdapterEvent.TimedOutAndRestarting(null));
      process.destroy();
      doInit();
      return Result.Err(
        new LocalOCRError.Other("\"Manga OCR\" took too long to respond and was restarted")
      );
    }
  }

  private void fatalFailure(String errorMsg) {
    isReady = false;
    dispatchEvent(new OCRAdapterEvent.FailedFatally(errorMsg));
  }

  public void destroy() {
    if (process != null) {
      process.destroy();
    }
  }
}
