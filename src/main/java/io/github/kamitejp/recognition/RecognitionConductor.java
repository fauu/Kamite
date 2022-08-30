package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.status.ProgramStatus;

public class RecognitionConductor {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Platform platform;
  private final ProgramStatus status;
  private final Consumer<RecognizerEvent> recognizerEventCb;
  private final Consumer<ChunkVariants> chunkVariantsCb;
  private final Consumer<String> notifyErrorFn;
  private final Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn;
  private Recognizer recognizer;

  public RecognitionConductor(
    Platform platform,
    Config config,
    ProgramStatus status,
    Consumer<RecognizerEvent> recognizerEventCb,
    Consumer<ChunkVariants> chunkVariantsCb,
    Consumer<String> notifyErrorFn,
    Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn
  ) {
    this.platform = platform;
    this.status = status;
    this.recognizerEventCb = recognizerEventCb;
    this.chunkVariantsCb = chunkVariantsCb;
    this.notifyErrorFn = notifyErrorFn;
    this.updateAndSendRecognizerStatusFn = updateAndSendRecognizerStatusFn;

    initRecognizer(config);
  }

  public void destroy() {
    if (recognizer != null) {
      recognizer.destroy();
    }
  }

  private void initRecognizer(Config config) {
    var unavailable = true;
    try {
      var engineRes = OCREngine.uninitializedFromConfig(config);
      if (engineRes.isErr()) {
        LOG.error("Error setting up OCR engine for initialization: {}", engineRes.err());
      } else {
        var engine = engineRes.get();
        if (!(engine instanceof OCREngine.None)) {
          platform.initOCR(engine);
          recognizer = new Recognizer(
            platform,
            engine,
            status.isDebug(),
            recognizerEventCb
          );
          unavailable = false;
        }
      }
    } catch (PlatformOCRInitializationException.MissingDependencies e) {
      LOG.error(
        "Text recognition will not be available due to missing dependencies: {}",
        () -> String.join(", ", e.getDependencies())
      );
    } catch (PlatformOCRInitializationException e) {
      throw new RuntimeException("Unhandled PlatformOCRInitializationException", e);
    } catch (RecognizerInitializationException e) {
      if (e.getMessage() != null) {
        LOG.error(e::getMessage);
      } else {
        LOG.error("Could not initialize Recognizer. See stderr for the stack trace");
        e.printStackTrace();
      }
    }
    if (unavailable) {
      updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.UNAVAILABLE);
    }
  }

  public void recognizeRegion(Rectangle region, boolean autoNarrow) {
    LOG.debug("Handling region recognition request ({})", region);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(
      region,
      TextOrientation.HORIZONTAL,
      /* autoBlockHeuristic */ autoNarrow ? AutoBlockHeuristic.GAME_TEXTBOX : null
    );
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeRegion(
    Rectangle region,
    TextOrientation textOrientation,
    AutoBlockHeuristic autoBlockHeuristic
  ) {
    var screenshotRes = platform.takeAreaScreenshot(region);
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    if (autoBlockHeuristic != null) {
      doRecognizeAutoBlockImageProvided(screenshotRes.get(), textOrientation, autoBlockHeuristic);
    } else {
      doRecognizeBox(screenshotRes.get(), textOrientation);
    }
  }

  public void recognizeManualBlockDefault() {
    recognizeManualBlock(TextOrientation.UNKNOWN);
  }

  public void recognizeManualBlock(TextOrientation textOrientation) {
    LOG.debug("Handling manual block recognition request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var areaRes = platform.getUserSelectedArea();
    if (areaRes.isErr()) {
      var errorNotification = switch (areaRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not get user screen area selection";
      };
      recognitionAbandon(errorNotification, areaRes.err());
      return;
    }

    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(areaRes.get(), textOrientation, /* heuristic */ null);
    // doRecognizeRegion(areaRes.get(), /* heuristic */ AutoBlockHeuristic.GAME_TEXTBOX); // DEV
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeAutoBlockDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_FULL);
  }

  public void recognizeAutoBlockColumnDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_SINGLE_COLUMN);
  }

  public void recognizeImageProvided(BufferedImage img) {
    LOG.debug("Handling image provided recognition request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeBox(img, TextOrientation.UNKNOWN);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  private void recognizeAutoBlock(
    PointSelectionMode mode, TextOrientation textOrientation, AutoBlockHeuristic heuristic
  ) {
    LOG.debug(
      "Handling auto block recognition request (mode = {}, heuristic = {})", mode, heuristic
    );
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var selectionRes = platform.getUserSelectedPoint(mode);
    if (selectionRes.isErr()) {
      var errorNotification = switch (selectionRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could get user screen point selection";
      };
      recognitionAbandon(errorNotification, selectionRes.err());
      return;
    }

    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);

    var point = selectionRes.get();
    var screenshotRes = platform.takeAreaScreenshot(
      Rectangle.around(point, Recognizer.AUTO_BLOCK_AREA_SIZE)
    );
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    doRecognizeAutoBlockImageProvided(screenshotRes.get(), textOrientation, heuristic);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  public void recognizeAutoBlockImageProvided(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic mode
  ) {
    LOG.debug("Handling auto block image recognition request (mode = {})", mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeAutoBlockImageProvided(img, textOrientation, mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeAutoBlockImageProvided(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic heuristic
  ) {
    var maybeBlockImg = recognizer.autoNarrowToTextBlock(img, heuristic);
    if (maybeBlockImg.isEmpty()) {
      var msg = "Text block detection has failed";
      notifyErrorFn.accept(msg);
      LOG.info(msg);
      return;
    }
    doRecognizeBox(maybeBlockImg.get(), textOrientation);
  }

  private void doRecognizeBox(BufferedImage img, TextOrientation textOrientation) {
    var recognitionRes = recognizer.recognizeBox(img, textOrientation);
    if (recognitionRes.isErr()) {
      var errorNotification = switch (recognitionRes.err()) {
        case SELECTION_CANCELLED -> null;
        case INPUT_TOO_SMALL     -> "Input image is too small";
        case ZERO_VARIANTS       -> "Could not recognize any text";
        default -> "Box recognition has failed.\nCheck control window or console for errors";
      };
      recognitionAbandon(errorNotification, recognitionRes.err());
      return;
    }
    chunkVariantsCb.accept(recognitionRes.get().chunkVariants());
  }

  private void recognitionAbandon(String errorNotification, RecognitionOpError errorToLog) {
    if (errorNotification != null) {
      notifyErrorFn.accept(errorNotification);
    }
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
    if (errorToLog != null) {
      recognitionLogError(errorToLog);
    }
  }

  private static void recognitionLogError(RecognitionOpError reason) {
    switch (reason) { // NOPMD - misidentifies as non-exhaustive
      case OCR_UNAVAILABLE ->
        LOG.error("No OCR engine is available for use");
      case SCREENSHOT_API_COMMUNICATION_FAILED ->
        LOG.error("Failed to communicate with the screenshot API");
      case SELECTION_CANCELLED ->
        LOG.debug("Screen area/point selection was cancelled by the user");
      case SELECTION_FAILED ->
        LOG.error("Failed to perform screen area selection");
      case SCREENSHOT_FAILED ->
        LOG.error("Failed to take a screenshot");
      case INPUT_TOO_SMALL ->
        LOG.error("Input image is too small");
      case OCR_ERROR ->
        LOG.error("OCR failed abnormally");
      case ZERO_VARIANTS ->
        LOG.debug("Could not recognize text");
    }
  }
}
