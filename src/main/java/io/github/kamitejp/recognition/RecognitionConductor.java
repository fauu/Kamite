package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformOCRInfrastructureInitializationException;
import io.github.kamitejp.recognition.configuration.MangaOCROCRConfiguration;
import io.github.kamitejp.recognition.configuration.MangaOCROnlineOCRConfiguration;
import io.github.kamitejp.recognition.configuration.OCRConfiguration;
import io.github.kamitejp.recognition.configuration.TesseractOCRConfiguration;
import io.github.kamitejp.status.ProgramStatus;

public class RecognitionConductor {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Platform platform;
  private final ProgramStatus status;
  private final Consumer<RecognizerEvent> recognizerEventCb;
  private final Consumer<UnprocessedChunkVariants> chunkVariantsCb;
  private final Consumer<String> notifyUserOfErrorFn;
  private final Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn;
  private Recognizer recognizer;

  public RecognitionConductor(
    Platform platform,
    ProgramStatus status,
    Consumer<RecognizerEvent> recognizerEventCb,
    Consumer<UnprocessedChunkVariants> chunkVariantsCb,
    Consumer<String> notifyUserOfErrorFn,
    Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn
  ) {
    this.platform = platform;
    this.status = status;
    this.recognizerEventCb = recognizerEventCb;
    this.chunkVariantsCb = chunkVariantsCb;
    this.notifyUserOfErrorFn = notifyUserOfErrorFn;
    this.updateAndSendRecognizerStatusFn = updateAndSendRecognizerStatusFn;
  }

  // XXX: Move?
  record OCRAdapterID(
    Class<? extends OCRAdapter<?>> adapterClass,
    OCRAdapterInitParams initParams
  ) {}

  public void initRecognizer(Config config) {
    var configurations = config.ocr().configurations().stream()
      .<OCRConfiguration<?, ?, ?>>map(c ->
        switch (c.engine()) {
          case TESSERACT       -> new TesseractOCRConfiguration(c);
          case MANGAOCR        -> new MangaOCROCRConfiguration(c);
          case MANGAOCR_ONLINE -> new MangaOCROnlineOCRConfiguration(c);
          default -> throw new IllegalStateException("XXX Unimplemented");
        }
      )
        .toList();

    var adapters = new HashMap<OCRAdapterID, OCRAdapter<? extends OCRAdapterOCRParams>>(8);

    for (var configuration : configurations) {
      var initParams = configuration.getAdapterInitParams();
      @SuppressWarnings("unlikely-arg-type") var maybeExistingAdapter = adapters.get(initParams);
      if (maybeExistingAdapter == null) {
        configuration.createAdapter(platform);
        var newAdapter = configuration.getAdapter();
        adapters.put(
          new OCRAdapterID((Class<? extends OCRAdapter<?>>) newAdapter.getClass(), initParams),
          configuration.getAdapter()
        );
      } else {
          Map<Class<? extends OCRConfiguration<?, ?, ?>>, Class<? extends OCRAdapter<?>>>
            configurationToAdapter = Map.of(
              TesseractOCRConfiguration.class, TesseractAdapter.class,
              MangaOCROCRConfiguration.class, MangaOCRController.class,
              MangaOCROnlineOCRConfiguration.class, MangaOCRHFAdapter.class
            );
          Class<? extends OCRAdapter<?>> adapterClass =
            configurationToAdapter.get(configuration.getClass());
          if (adapterClass.isInstance(maybeExistingAdapter)) {
            @SuppressWarnings("unchecked")
            OCRConfiguration<?, ?, OCRAdapter<?>> rawConfiguration =
              (OCRConfiguration<?, ?, OCRAdapter<?>>) configuration;
            rawConfiguration.setAdapter((OCRAdapter<?>) maybeExistingAdapter);
          } else {
            throw new IllegalStateException("Configuration/Adapter mismatch");
          }
        // XXX: Remove if the above works
        // switch (configuration) {
        //   case TesseractOCRConfiguration c ->
        //     c.setAdapter((TesseractAdapter) maybeExistingAdapter);
        //   case MangaOCROCRConfiguration c ->
        //     c.setAdapter((MangaOCRController) maybeExistingAdapter);
        //   case MangaOCROnlineOCRConfiguration c ->
        //     c.setAdapter((MangaOCRHFAdapter) maybeExistingAdapter);
        //   default -> throw new IllegalStateException("Configuration/Adapter mismatch");
        // }
      }
    }

    // XXX: Abort here when no configurations with validated adapter params (?)

    var unavailable = true;
    try {
      platform.initOCRInfrastructure();

      for (var adapter : adapters.values()) {
        if (adapter instanceof StatefulOCRAdapter statefulAdapter) {
          var res = statefulAdapter.init();
          if (res.isErr()) {
            LOG.error("Could not initialize Recognizer: {}", res.err());
            return;
          }
        }
      }

      recognizer = new Recognizer(platform, configurations, status.isDebug(), recognizerEventCb);
      unavailable = false;
    } catch (PlatformOCRInfrastructureInitializationException.MissingDependencies e) {
      LOG.error(
        "Text recognition will not be available due to missing dependencies: {}",
        () -> String.join(", ", e.getDependencies())
      );
    } catch (PlatformOCRInfrastructureInitializationException e) {
      throw new RuntimeException(
        "Unhandled PlatformOCRInfrastructureInitializationException",
        e
      );
    } catch (RecognizerInitializationException e) {
      var message = e.getMessage();
      if (message != null) {
        LOG.error(message);
      } else {
        LOG.error("Could not initialize Recognizer. See stderr for the stack trace");
        e.printStackTrace();
      }
    }
    if (unavailable) {
      updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.UNAVAILABLE);
    }
  }

  public void destroy() {
    if (recognizer != null) {
      recognizer.destroy();
    }
  }

  public void recognizeRegion(Rectangle region, boolean autoNarrow) {
    LOG.debug("Handling region recognition request ({})", region);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(
      region,
      /* autoBlockHeuristic */ autoNarrow ? AutoBlockHeuristic.GAME_TEXTBOX : null
    );
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeRegion(Rectangle region, AutoBlockHeuristic autoBlockHeuristic) {
    var screenshotRes = platform.takeAreaScreenshot(region);
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    if (autoBlockHeuristic != null) {
      doRecognizeAutoBlockGivenImage(screenshotRes.get(), autoBlockHeuristic);
    } else {
      doRecognizeBox(screenshotRes.get());
    }
  }

  public void recognizeManualBlock() {
    LOG.debug("Handling manual block recognition request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var areaRes = platform.getUserSelectedArea();
    if (areaRes.isErr()) {
      var errorNotification = switch (areaRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not get user screen area selection";
      };
      recognitionAbandon(errorNotification, areaRes.err());
      return;
    }

    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(areaRes.get(), /* heuristic */ null);
    // doRecognizeRegion(areaRes.get(), /* heuristic */ AutoBlockHeuristic.GAME_TEXTBOX); // DEV
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeManualBlockRotated() {
    LOG.debug("Handling manual rotated block recognition request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var selectedPoints = new Point[3];
    for (var i = 0; i < 3; i++) {
      var selectionRes = platform.getUserSelectedPoint(PointSelectionMode.SELECT);
      if (selectionRes.isErr()) {
        var errorNotification = switch (selectionRes.err()) {
          case SELECTION_CANCELLED -> null;
          default -> "Could not get user screen point selection";
        };
        recognitionAbandon(errorNotification, selectionRes.err());
        return;
      }
      selectedPoints[i] = selectionRes.get();
    }

    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);

    var maybeRotatedBlock = Recognizer.computeRotatedBlock(selectedPoints);
    if (maybeRotatedBlock.isEmpty()) {
      recognitionAbandon("Selection incorrect", RecognitionOpError.SELECTION_INCORRECT);
      return;
    }
    var rotatedBlock = maybeRotatedBlock.get();

    var screenshotRes = platform.takeAreaScreenshot(rotatedBlock.boundingRectangle());
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    var straightened = Recognizer.straightenRotatedBlockImage(rotatedBlock, screenshotRes.get());
    doRecognizeBox(straightened);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeAutoBlockDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, AutoBlockHeuristic.MANGA_FULL);
  }

  public void recognizeAutoBlockColumnDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, AutoBlockHeuristic.MANGA_SINGLE_COLUMN);
  }

  public void recognizeGivenImage(BufferedImage img) {
    LOG.debug("Handling image given recognition request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeBox(img);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  private void recognizeAutoBlock(PointSelectionMode mode, AutoBlockHeuristic heuristic) {
    LOG.debug(
      "Handling auto block recognition request (mode = {}, heuristic = {})", mode, heuristic
    );
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var selectionRes = platform.getUserSelectedPoint(mode);
    if (selectionRes.isErr()) {
      var errorNotification = switch (selectionRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not get user screen point selection";
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
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    doRecognizeAutoBlockGivenImage(screenshotRes.get(), heuristic);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  public void recognizeAutoBlockGivenImage(BufferedImage img, AutoBlockHeuristic mode) {
    LOG.debug("Handling auto block image recognition request (mode = {})", mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeAutoBlockGivenImage(img, mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeAutoBlockGivenImage(BufferedImage img, AutoBlockHeuristic heuristic) {
    var maybeBlockImg = recognizer.autoNarrowToTextBlock(img, heuristic);
    if (maybeBlockImg.isEmpty()) {
      var msg = "Text block detection has failed";
      notifyUserOfErrorFn.accept(msg);
      LOG.info(msg);
      return;
    }
    doRecognizeBox(maybeBlockImg.get());
  }

  private void doRecognizeBox(BufferedImage img) {
    var recognitionRes = recognizer.recognizeBox(img);
    if (recognitionRes.isErr()) {
      var errorNotification = switch (recognitionRes.err()) {
        case SELECTION_CANCELLED -> null;
        case INPUT_TOO_SMALL     -> "Input image is too small";
        case ZERO_VARIANTS       -> "Did not recognize any text";
        default -> "OCR has failed.\nCheck control window or console for errors";
      };
      recognitionAbandon(errorNotification, recognitionRes.err());
      return;
    }
    chunkVariantsCb.accept(recognitionRes.get().chunkVariants());
  }

  private void recognitionAbandon(String errorNotification, RecognitionOpError errorToLog) {
    if (errorNotification != null) {
      notifyUserOfErrorFn.accept(errorNotification);
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
        LOG.error("Failed to perform screen area/point selection");
      case SELECTION_INCORRECT ->
        LOG.error("User's screen area/point selection is incorrect");
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

