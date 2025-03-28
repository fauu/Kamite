package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import io.github.kamitejp.recognition.configuration.MangaOCROCRConfiguration;
import io.github.kamitejp.recognition.configuration.MangaOCROnlineOCRConfiguration;
import io.github.kamitejp.recognition.configuration.OCRConfiguration;
import io.github.kamitejp.recognition.configuration.TesseractOCRConfiguration;
import io.github.kamitejp.status.ProgramStatus;
import io.github.kamitejp.util.Executor;

public class RecognitionConductor {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  // XXX
  public static Class<?> extractThirdTypeParameter(Class<?> configClass) {
    var genericSuperclass = configClass.getGenericSuperclass();
    
    if (!(genericSuperclass instanceof ParameterizedType)) {
        throw new IllegalArgumentException("Class is not parameterized");
    }
    
    ParameterizedType paramType = (ParameterizedType) genericSuperclass;
    Type[] typeArgs = paramType.getActualTypeArguments();
    
    if (typeArgs.length < 3) {
        throw new IllegalArgumentException("Class has fewer than 3 type parameters");
    }
    
    var thirdArg = typeArgs[2];
    
    if (thirdArg instanceof Class) {
        return (Class<?>) thirdArg;
    } else if (thirdArg instanceof ParameterizedType) {
        return (Class<?>) ((ParameterizedType) thirdArg).getRawType();
    }
    
    throw new IllegalArgumentException("Third type parameter is not a Class");
  }

  private final Platform platform;
  private final ProgramStatus status;
  private final Consumer<RecognizerEvent> recognizerEventCb;
  private final Consumer<UnprocessedChunkVariants> chunkVariantsCb;
  private final Consumer<String> notifyUserOfErrorFn;
  private final Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn;
  private Recognizer recognizer;
  private List<OCRConfiguration<?, ?, ?>> ocrConfigurations;
  private HashMap<Integer, StatefulOCRAdapter> statefulOCRAdapters = new HashMap<>();
  
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
    ocrConfigurations = config.ocr().configurations().stream()
      .<OCRConfiguration<?, ?, ?>>map(c ->
        switch (c.engine()) {
          case TESSERACT       -> new TesseractOCRConfiguration(c);
          case MANGAOCR        -> new MangaOCROCRConfiguration(c);
          case MANGAOCR_ONLINE -> new MangaOCROnlineOCRConfiguration(c);
          default -> throw new IllegalStateException("XXX Unimplemented");
        }
      ).toList();

    var adapters = new HashMap<OCRAdapterID, OCRAdapter<? extends OCRAdapterOCRParams>>(8);

    var unavailable = true;

    try {
      // For each configuration, either create a new adapter or use an existing one (in case two
      // configurations need the same adapter with the same init params)
      for (var configuration : ocrConfigurations) {
        configuration.setStatus(new OCRConfigurationStatus.Initializing(null));

        var adapterInitParams = configuration.getAdapterInitParams();
        var adapterClass =
          (Class<? extends OCRAdapter<?>>) extractThirdTypeParameter(configuration.getClass());
        var existingAdapter = adapters.get(new OCRAdapterID(adapterClass, adapterInitParams));

        if (existingAdapter == null) {
          try {
            configuration.createAdapter(platform);
          } catch (OCRAdapterPreinitializationException e) {
            throw new RecognizerInitializationException( // NOPMD
              "Could not preinitialize the adapter for %s: %s"
                .formatted(configuration.getName(), e.getMessage())
            );
          }
          var newAdapter = configuration.getAdapter();
          @SuppressWarnings("unchecked")
          var newAdapterClass = (Class<? extends OCRAdapter<?>>) newAdapter.getClass();
          adapters.put(new OCRAdapterID(newAdapterClass, adapterInitParams), configuration.getAdapter());
          if (!(newAdapter instanceof StatefulOCRAdapter)) {
            configuration.setStatus(new OCRConfigurationStatus.Available());
          }
        } else {
          var rawConfiguration = (OCRConfiguration<?, ?, OCRAdapter<?>>) configuration;
          rawConfiguration.setAdapter((OCRAdapter<?>) existingAdapter);
        }
      }

      // XXX: Abort here when no configurations with validated adapter params (?)

      platform.initOCRInfrastructure();

      sendOCRConfigurationsListUpdatedRecognizerEvent();

      // Initialize stateful adapters
      int currentId = 0;
      for (var adapter : adapters.values()) {
        if (adapter instanceof StatefulOCRAdapter statefulAdapter) {
          final var id = currentId;
          Executor.get().execute(() -> statefulAdapter.init(id, this::handleOCRAdapterEvent));
          statefulOCRAdapters.put(id, statefulAdapter);
          currentId++;
        }
      }

      recognizer = new Recognizer(platform, ocrConfigurations, status.isDebug(), recognizerEventCb);
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

  // XXX
  private void handleOCRAdapterEvent(int adapterId, OCRAdapterEvent event) {
    var clazz = statefulOCRAdapters.get(adapterId).getClass();
    if (
      event instanceof OCRAdapterEvent.TimedOutAndRestarting
      || event instanceof OCRAdapterEvent.FailedFatally
    ) {
      LOG.error("{}: {}", clazz, event); 
    } else {
      LOG.info("{}: {}", clazz, event); 
    }

    var configurationsListUpdated = false;
    for (var configuration : ocrConfigurations) {
      var adapter = configuration.getAdapter();
      if (adapter instanceof StatefulOCRAdapter statefulAdapter) {
        if (adapterId == statefulAdapter.getID()) {
          var prevStatus = configuration.getStatus();
          var newStatus = switch(event) {
            case OCRAdapterEvent.Launching e ->
              new OCRConfigurationStatus.Initializing(e.msg());
            case OCRAdapterEvent.Launched _ ->
              prevStatus;
            case OCRAdapterEvent.StartedExtraSetup e ->
              new OCRConfigurationStatus.Initializing(e.msg());
            case OCRAdapterEvent.Initialized _ ->
              new OCRConfigurationStatus.Available();
            case OCRAdapterEvent.TimedOutAndRestarting e ->
              new OCRConfigurationStatus.TimedOutAndReinitializing(e.msg());
            case OCRAdapterEvent.FailedFatally e ->
              new OCRConfigurationStatus.FailedFatally(e.msg());
          };
          if (!newStatus.equals(prevStatus)) {
            configuration.setStatus(newStatus);
            configurationsListUpdated = true;
          }
        }
      }
    }

    if (configurationsListUpdated) {
      sendOCRConfigurationsListUpdatedRecognizerEvent();
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

  private void sendOCRConfigurationsListUpdatedRecognizerEvent() {
    recognizerEventCb.accept(
      new RecognizerEvent.OCRConfigurationListUpdated(
        ocrConfigurations.stream()
          .map(c -> new OCRConfigurationInfo(c.getName(), c.getStatus()))
          .toList()
      )
    );
  }
}

