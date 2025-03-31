package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformOCRInfrastructureInitializationException;
import io.github.kamitejp.recognition.OCRConfigurationStatus.ReinitializingAfterAdapterTimeout;
import io.github.kamitejp.recognition.configuration.MangaOCROCRConfiguration;
import io.github.kamitejp.recognition.configuration.MangaOCROnlineOCRConfiguration;
import io.github.kamitejp.recognition.configuration.OCRConfiguration;
import io.github.kamitejp.recognition.configuration.TesseractOCRConfiguration;
import io.github.kamitejp.status.ProgramStatus;
import io.github.kamitejp.util.Executor;
import io.github.kamitejp.util.Reflection;
import io.github.kamitejp.util.Strings;

public class RecognitionConductor {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Platform platform;
  private final ProgramStatus status;
  private final Consumer<RecognizerEvent> recognizerEventCb;
  private final Consumer<UnprocessedChunkVariants> chunkVariantsCb;
  private final Consumer<String> notifyUserOfErrorFn;
  private final Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn;
  private Recognizer recognizer;
  private List<OCRConfiguration<?, ?, ?>> ocrConfigurations;
  private HashMap<OCRAdapterID, OCRAdapter<? extends OCRAdapterOCRParams>> ocrAdapters;
  private HashMap<Integer, StatefulOCRAdapter> statefulOCRAdapters = new HashMap<>();

  public RecognitionConductor(
      Platform platform,
      ProgramStatus status,
      Consumer<RecognizerEvent> recognizerEventCb,
      Consumer<UnprocessedChunkVariants> chunkVariantsCb,
      Consumer<String> notifyUserOfErrorFn,
      Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn) {
    this.platform = platform;
    this.status = status;
    this.recognizerEventCb = recognizerEventCb;
    this.chunkVariantsCb = chunkVariantsCb;
    this.notifyUserOfErrorFn = notifyUserOfErrorFn;
    this.updateAndSendRecognizerStatusFn = updateAndSendRecognizerStatusFn;
  }

  private record OCRAdapterID(
    String adapterClassName,
    OCRAdapterInitParams initParams
  ) {}

  public void initRecognizer(Config config) {
    ocrConfigurations = config.ocr().configurations().stream()
      .<OCRConfiguration<?, ?, ?>>map(c -> switch (c.engine()) {
        case TESSERACT       -> new TesseractOCRConfiguration(c);
        case MANGAOCR        -> new MangaOCROCRConfiguration(c);
        case MANGAOCR_ONLINE -> new MangaOCROnlineOCRConfiguration(c);
        // XXX
        default -> throw new IllegalStateException("XXX Unimplemented");
      }).toList();
    ocrAdapters = new HashMap<>(8);

    var hasAvailableStatelessConfigurations = false;
    try {
      // For each configuration, either create a new adapter or assign an existing one
      // (in case two configurations use the same type of adapter with the same init params)
      for (var configuration : ocrConfigurations) {
        configuration.setStatus(new OCRConfigurationStatus.Initializing(null));

        var adapterInitParams = configuration.getAdapterInitParams();
        var existingAdapter = ocrAdapters.get(
          new OCRAdapterID(
            Reflection.extractNthTypeParameter(configuration.getClass(), 2).getName(),
            adapterInitParams));

        if (existingAdapter != null) {
          @SuppressWarnings("unchecked")
          var conf = (OCRConfiguration<?, ?, OCRAdapter<?>>) configuration;
          conf.setAdapter(existingAdapter);
        } else {
          try {
            configuration.createAdapter(platform);
            var newAdapter = configuration.getAdapter();
            ocrAdapters.put(
              new OCRAdapterID(newAdapter.getClass().getName(), adapterInitParams),
              newAdapter);
          } catch (OCRAdapterPreInitializationException e) {
            var failedMsg = "Could not preinitialize Adapter for OCR configuration '%s': %s"
              .formatted(configuration.getName(), e.getMessage());
            // XXX: Make sure this is logged downstream
            configuration.setStatus(new OCRConfigurationStatus.AdapterFailedFatally(failedMsg));
          }
        }

        var isWithNonFailedStatelessAdapter =
          !(configuration.getStatus() instanceof OCRConfigurationStatus.AdapterFailedFatally)
          && !(configuration.getAdapter() instanceof StatefulOCRAdapter);
        if (isWithNonFailedStatelessAdapter) {
          configuration.setStatus(new OCRConfigurationStatus.Available());
          hasAvailableStatelessConfigurations = true;
          // Stateful Adapters need an initialization step (below)
        }
      }

      platform.initOCRInfrastructure();

      sendOCRConfigurationRecordsUpdatedRecognizerEvent();

      // Initialize Stateful Adapters
      var currentId = 0;
      for (var adapter : ocrAdapters.values()) {
        if (adapter instanceof StatefulOCRAdapter statefulAdapter) {
          final var id = currentId;
          Executor.get().execute(() -> statefulAdapter.init(id, this::handleOCRAdapterEvent));
          statefulOCRAdapters.put(id, statefulAdapter);
          currentId++;
        }
      }

      if (!hasAvailableStatelessConfigurations && currentId == 0) {
        // This is only for the case of no Configuration using a Stateful Adapter because the latter
        // could still be in the process of initializing
        updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.UNAVAILABLE);
        LOG.info("OCR will not be available because there are no available OCR Configurations");
      } else {
        recognizer = new Recognizer(
          platform,
          ocrConfigurations,
          status.isDebug(),
          recognizerEventCb
        );
      }
    } catch (PlatformOCRInfrastructureInitializationException.MissingDependencies e) {
      LOG.error(
          "OCR will not be available due to missing dependencies: {}",
          () -> String.join(", ", e.getDependencies()));
    } catch (PlatformOCRInfrastructureInitializationException e) {
      throw new RuntimeException("Unhandled PlatformOCRInfrastructureInitializationException", e);
    } catch (RecognizerInitializationException e) {
      var message = e.getMessage();
      if (message != null) {
        LOG.error(message);
      } else {
        LOG.error("Could not initialize Recognizer. See stderr for the stack trace");
        e.printStackTrace();
      }
    }
  }

  public void setActiveOCRConfiguration(String ocrConfigurationName) {
    if (Strings.isNullOrEmpty(ocrConfigurationName)) {
      LOG.error("Tried to set Active OCR Configuration with empty configuration name");
      return;
    }
    // NOTE: We could use a HashMap but it's probably not worth it given the tiny number of
    //       Configurations
    var configuration = ocrConfigurations.stream()
      .filter(c -> c.getName().equals(ocrConfigurationName))
      .findFirst();
    if (!configuration.isPresent()) {
      LOG.error(
        "Tried to set the active OCR configuration but it was not found (name = '{}')",
        ocrConfigurationName);
      return;
    }
    recognizer.setActiveOCRConfiguration(configuration.get());
  }

  // Only applies to Stateful Adapters
  private void handleOCRAdapterEvent(int adapterId, OCRAdapterEvent event) {
    var adapterClass = statefulOCRAdapters.get(adapterId).getClass();
    if (event instanceof OCRAdapterEvent.TimedOutAndRestarting
        || event instanceof OCRAdapterEvent.FailedFatally) {
      LOG.error("{}: {}", adapterClass, event);
    } else {
      LOG.info("{}: {}", adapterClass, event);
    }

    // Update Configuration Status according to the Event
    var configurationsListUpdated = false;
    for (var configuration : ocrConfigurations) {
      if (configuration.getAdapter() instanceof StatefulOCRAdapter statefulAdapter) {
        if (adapterId != statefulAdapter.getID()) {
          continue;
        }
        var prevStatus = configuration.getStatus();
        var newStatus = switch (event) {
          case OCRAdapterEvent.Launching e ->
            new OCRConfigurationStatus.Initializing(e.msg());

          case OCRAdapterEvent.Launched _ ->
            prevStatus;

          case OCRAdapterEvent.StartedExtraSetup e ->
            new OCRConfigurationStatus.Initializing(e.msg());

          case OCRAdapterEvent.Initialized _ ->
            new OCRConfigurationStatus.Available();

          case OCRAdapterEvent.TimedOutAndRestarting e ->
            new OCRConfigurationStatus.ReinitializingAfterAdapterTimeout(e.msg());

          case OCRAdapterEvent.FailedFatally e ->
            new OCRConfigurationStatus.AdapterFailedFatally(e.msg());
        };
        if (!newStatus.equals(prevStatus)) {
          configuration.setStatus(newStatus);
          configurationsListUpdated = true;
        }
      }
    }

    if (configurationsListUpdated) {
      sendOCRConfigurationRecordsUpdatedRecognizerEvent();
    }

    var hasNonFailedConfigurations = false;
    var hasNonReinitializingConfigurations = false;
    for (var configuration : ocrConfigurations) {
      var configurationStatus = configuration.getStatus();
      if (!(configurationStatus instanceof OCRConfigurationStatus.AdapterFailedFatally)) {
        hasNonFailedConfigurations = true;
      }
      if (!(configurationStatus instanceof ReinitializingAfterAdapterTimeout)) {
        hasNonReinitializingConfigurations = true;
      }
      if (hasNonReinitializingConfigurations && hasNonFailedConfigurations) break;
    }
    if (!hasNonFailedConfigurations) {
      updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.UNAVAILABLE);
      LOG.info("OCR will not be available because all OCR Configurations' Adapters have failed");
    } else if (!hasNonReinitializingConfigurations) {
      // Reinitializing
      // XXX: Verify that this works properly (including the else if below)
      updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.INITIALIZING);
    } else if (status.getRecognizerStatus().getKind() == RecognizerStatus.Kind.INITIALIZING) {
      // Done reinitializing
      updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
      // XXX: Need to update `availableCommands`?
    }
  }

  public void destroy() {
    if (recognizer != null) {
      recognizer.destroy();
    }
    for (var adapter : ocrAdapters.values()) {
      adapter.destroy();
    }
  }

  public void recognizeRegion(String ocrConfigurationName, Rectangle region, boolean autoNarrow) {
    LOG.debug("Handling region recognition request ({})", region);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(
      ocrConfigurationName,
      region,
      /* autoBlockHeuristic */ autoNarrow ? AutoBlockHeuristic.GAME_TEXTBOX : null
    );
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeRegion(
      String ocrConfigurationName,
      Rectangle region,
      AutoBlockHeuristic autoBlockHeuristic) {
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
      doRecognizeAutoBlockGivenImage(ocrConfigurationName, screenshotRes.get(), autoBlockHeuristic);
    } else {
      doRecognizeBox(ocrConfigurationName, screenshotRes.get());
    }
  }

  public void recognizeManualBlock(String ocrConfigurationName) {
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
    doRecognizeRegion(ocrConfigurationName, areaRes.get(), /* heuristic */ null);
    // doRecognizeRegion(areaRes.get(), /* heuristic */
    // AutoBlockHeuristic.GAME_TEXTBOX); // DEV
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeManualBlockRotated(String ocrConfigurationName) {
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
    doRecognizeBox(ocrConfigurationName, straightened);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeAutoBlockDefault(String ocrConfigurationName, PointSelectionMode mode) {
    recognizeAutoBlock(ocrConfigurationName, mode, AutoBlockHeuristic.MANGA_FULL);
  }

  public void recognizeAutoBlockColumnDefault(
      String ocrConfigurationName, PointSelectionMode mode) {
    recognizeAutoBlock(ocrConfigurationName, mode, AutoBlockHeuristic.MANGA_SINGLE_COLUMN);
  }

  public void recognizeGivenImage(String ocrConfigurationName, BufferedImage img) {
    LOG.debug("Handling Image Given Recognition Request");
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeBox(ocrConfigurationName, img);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  private void recognizeAutoBlock(
      String ocrConfigurationName,
      PointSelectionMode mode,
      AutoBlockHeuristic heuristic) {
    LOG.debug(
        "Handling auto block recognition request (mode = {}, heuristic = {})", mode, heuristic);
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
        Rectangle.around(point, Recognizer.AUTO_BLOCK_AREA_SIZE));
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    doRecognizeAutoBlockGivenImage(ocrConfigurationName, screenshotRes.get(), heuristic);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  public void recognizeAutoBlockGivenImage(
      String ocrConfigurationName,
      BufferedImage img,
      AutoBlockHeuristic mode) {
    LOG.debug("Handling auto block image recognition request (mode = {})", mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeAutoBlockGivenImage(ocrConfigurationName, img, mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeAutoBlockGivenImage(
      String ocrConfigurationName,
      BufferedImage img,
      AutoBlockHeuristic heuristic) {
    var maybeBlockImg = recognizer.autoNarrowToTextBlock(img, heuristic);
    if (maybeBlockImg.isEmpty()) {
      var msg = "Text block detection has failed";
      notifyUserOfErrorFn.accept(msg);
      LOG.info(msg);
      return;
    }
    doRecognizeBox(ocrConfigurationName, maybeBlockImg.get());
  }

  private void doRecognizeBox(String ocrConfigurationName, BufferedImage img) {
    var recognitionRes = recognizer.recognizeBox(ocrConfigurationName, img);
    if (recognitionRes.isErr()) {
      var errorNotification = switch (recognitionRes.err()) {
        case SELECTION_CANCELLED -> null;
        case INPUT_TOO_SMALL -> "Input image is too small";
        case ZERO_VARIANTS -> "Did not recognize any text";
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
      case OCR_SYSTEM_UNAVAILABLE ->
        LOG.error("OCR system setup has not successfully completed");
      case OCR_CONFIGURATION_UNAVAILABLE ->
        LOG.error("The specified OCR configuration is not available");
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

  private void sendOCRConfigurationRecordsUpdatedRecognizerEvent() {
    recognizerEventCb.accept(
        new RecognizerEvent.OCRConfigurationRecordsUpdated(
            ocrConfigurations.stream()
                .map(c -> new OCRConfigurationRecord(c.getName(), c.getStatus()))
                .toList()));
  }
}
