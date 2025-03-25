package io.github.kamitejp.platform;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.recognition.OCRAdapter;

public class MangaOCRController implements OCRAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PIPX_DEFAULT_VENV_NAME = "manga-ocr";
  private static final int RECOGNITION_TIMEOUT_S = 8;

  private State state;
  // XXX
  // private final Consumer<MangaOCREvent> eventCb;
  private final String[] cmd;
  private Process process;
  private BufferedReader outputReader;

  public MangaOCRController(
    // XXX
    // Platform platform, String customPythonPath, Consumer<MangaOCREvent> eventCb
    Platform platform, String customPythonPath
    // XXX
  // ) throws MangaOCRInitializationException {
  ) {
    // XXX
    // this.eventCb = eventCb;

    var pythonPath = effectivePythonPath(platform, customPythonPath);
    if (pythonPath == null) {
    // XXX
      // throw new MangaOCRInitializationException(
      //   "pipx \"Manga OCR\" installation absent at default location."
      //   + " Please specify `ocr.mangaocr.pythonPath` in the config"
      // );
    }

    cmd = new String[] { pythonPath, platform.getMangaOCRAdapterPath().toString() };

    // XXX
    // start();
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

  // XXX
  //private void start() throws MangaOCRInitializationException {
  private void start() {
    state = State.STARTING;
    LOG.info("Starting \"Manga OCR\" using `{}`", cmd[0]);
    var pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true); // Needed to catch the "Downloading" messages
    try {
      process = pb.start();
      outputReader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
      );

      var ready = false;
      var sentDownloadingEvent = false;
      for (var line = outputReader.readLine(); line != null; line = outputReader.readLine()) {
        LOG.debug("Received output line from \"Manga OCR\": {}", line);
        if (!sentDownloadingEvent && line.startsWith("Downloading")) {
          LOG.info("\"Manga OCR\" is downloading its base model. This might take a while");
          //noinspection ObjectAllocationInLoop
          // XXX
          // eventCb.accept(new MangaOCREvent.StartedDownloadingModel());
          sentDownloadingEvent = true;
        }
        if ("READY".equals(line)) {
          ready = true;
          break;
        }
      }
      if (!ready) {
        state = State.FAILED;
        // XXX
        //throw new MangaOCRInitializationException("did not report readiness");
      }
    } catch (IOException e) {
      state = State.FAILED;
        // XXX
      //throw new MangaOCRInitializationException("error while reading initial output", e);
    }
    // XXX
    // eventCb.accept(new MangaOCREvent.Started());
    state = State.STARTED;
  }

  private final Supplier<String> outputLineSupplier = () -> {
    try {
      return outputReader.readLine();
    } catch (IOException e) {
      handleCrash("Error while reading \"Manga OCR\" output. See stderr for stack trace");
      e.printStackTrace();
    }
    return null;
  };

  public Optional<String> recognize(BufferedImage img) {
    if (state != State.STARTED) {
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
        return Optional.empty();
      }

      if (line.startsWith("Traceback (most")) {
        var errBuilder = new StringBuilder();
        String errLine = null;
        while ((errLine = outputReader.readLine()) != null) {
          errBuilder.append(errLine);
        }
        handleCrash("\"Manga OCR\" responded with an error: %s".formatted(errBuilder.toString()));
        return Optional.empty();
      }

      return Optional.of(line);
    } catch (IOException | InterruptedException | ExecutionException e) {
      handleCrash("Error while communicating with \"Manga OCR\". See stderr for stack trace");
      e.printStackTrace();
    } catch (TimeoutException e) {
      state = State.FAILED;
      // XXX
      // eventCb.accept(new MangaOCREvent.TimedOutAndRestarting());
      LOG.info("\"Manga OCR\" is taking too long to respond. Restarting");
      process.destroy();
      try {
        start();
      // XXX
      //} catch (MangaOCRInitializationException e1) {
      } catch (Exception e1) {
        handleCrash("Error while restarting \"Manga OCR\": %s".formatted(e1.getMessage()));
      }
    }
    return Optional.empty();
  }

  private void handleCrash(String errorMsg) {
    state = State.FAILED;
    // XXX
    // eventCb.accept(new MangaOCREvent.Crashed());
    LOG.error(errorMsg);
  }

  public void destroy() {
    if (process != null) {
      process.destroy();
    }
  }

  private enum State { STARTING, STARTED, FAILED }
}

