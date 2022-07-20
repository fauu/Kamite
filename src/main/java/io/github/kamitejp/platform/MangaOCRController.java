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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.recognition.ImageOps;

public class MangaOCRController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int RECOGNITION_TIMEOUT_S = 8;

  private State state;
  private final Consumer<MangaOCREvent> eventCb;
  private final String[] cmd;
  private final String launchMsgMethod;
  private Process process;
  private BufferedReader outputReader;

  public MangaOCRController(
    Platform platform, Consumer<MangaOCREvent> eventCb
  ) throws MangaOCRInitializationException {
    if (platform.getOS().getFamily() != OSFamily.UNIX) {
      throw new MangaOCRInitializationException("not supported on the current platform");
    }

    this.eventCb = eventCb;

    cmd = new String[] { "python", platform.getMangaOCRWrapperPath().toString() };
    var launchMsgMethod = "`python` executable in $PATH";
    var userLauncherPath = platform.getConfigDirPath()
      .map(p -> p.resolve(GenericPlatform.MANGAOCR_USER_LAUNCHER_SCRIPT_FILENAME))
      .orElse(null);
    if (userLauncherPath != null && Files.isExecutable(userLauncherPath)) {
      cmd[0] = userLauncherPath.toString();
      launchMsgMethod = "user-provided launcher script";
    }
    this.launchMsgMethod = launchMsgMethod;

    start();
  }

  private void start() throws MangaOCRInitializationException {
    state = State.STARTING;
    LOG.info("Starting “Manga OCR” using {}", launchMsgMethod);
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
        LOG.debug("Received output line from “Manga OCR”: {}", line);
        if (!sentDownloadingEvent && line.startsWith("Downloading")) {
          LOG.info("“Manga OCR” is downloading OCR model. This might take a while");
          //noinspection ObjectAllocationInLoop
          eventCb.accept(new MangaOCREvent.StartedDownloadingModel());
          sentDownloadingEvent = true;
        }
        if ("READY".equals(line)) {
          ready = true;
          break;
        }
      }
      if (!ready) {
        state = State.FAILED;
        throw new MangaOCRInitializationException("did not report readiness");
      }
    } catch (IOException e) {
      state = State.FAILED;
      throw new MangaOCRInitializationException("error while reading initial output", e);
    }
    eventCb.accept(new MangaOCREvent.Started());
    state = State.STARTED;
  }

  private final Supplier<String> outputLineSupplier = () -> {
    try {
      return outputReader.readLine();
    } catch (IOException e) {
      handleCrash("Error while reading “Manga OCR” output. See stderr for stack trace");
      e.printStackTrace();
    }
    return null;
  };

  public Optional<String> recognize(BufferedImage img) {
    if (state != State.STARTED) {
      throw new IllegalStateException("Attempted to use “Manga OCR” while it was not ready");
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
      var readFuture = CompletableFuture.supplyAsync(outputLineSupplier);
      return Optional.ofNullable(readFuture.get(RECOGNITION_TIMEOUT_S, TimeUnit.SECONDS));
    } catch (IOException | InterruptedException | ExecutionException e) {
      handleCrash("Error while using “Manga OCR”. See stderr for stack trace");
      e.printStackTrace();
    } catch (TimeoutException e) {
      state = State.FAILED;
      eventCb.accept(new MangaOCREvent.TimedOutAndRestarting());
      LOG.info("“Manga OCR” is taking too long to answer. Restarting");
      process.destroy();
      try {
        start();
      } catch (MangaOCRInitializationException e1) {
        handleCrash("Error while restarting “Manga OCR”: %s".formatted(e.getMessage()));
      }
    }
    return Optional.empty();
  }

  private void handleCrash(String errorMsg) {
    state = State.FAILED;
    eventCb.accept(new MangaOCREvent.Crashed());
    LOG.error(errorMsg);
  }

  public void destroy() {
    if (process != null) {
      process.destroy();
    }
  }

  private enum State { STARTING, STARTED, FAILED }
}

