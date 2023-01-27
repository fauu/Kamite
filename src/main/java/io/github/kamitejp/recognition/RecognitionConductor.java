package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.geometry.Direction;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.status.ProgramStatus;

public class RecognitionConductor {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Platform platform;
  private final ProgramStatus status;
  private final Consumer<RecognizerEvent> recognizerEventCb;
  private final Consumer<ChunkVariants> chunkVariantsCb;
  private final Consumer<String> notifyUserOfErrorFn;
  private final Consumer<RecognizerStatus.Kind> updateAndSendRecognizerStatusFn;
  private Recognizer recognizer;

  public RecognitionConductor(
    Platform platform,
    ProgramStatus status,
    Consumer<RecognizerEvent> recognizerEventCb,
    Consumer<ChunkVariants> chunkVariantsCb,
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

  public void initRecognizer(Config config) {
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
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    if (autoBlockHeuristic != null) {
      doRecognizeAutoBlockGivenImage(screenshotRes.get(), textOrientation, autoBlockHeuristic);
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
        default -> "Could not get user screen area selection";
      };
      recognitionAbandon(errorNotification, areaRes.err());
      return;
    }

    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(areaRes.get(), textOrientation, /* heuristic */ null);
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

    // (Points in screen coordinates)
    // Beginning of starting edge of text block (top-left of first possible character)
    var a = selectedPoints[0];
    // End of starting edge of text block (bottom-right or top-right of first possible character)
    var b = selectedPoints[1];
    // Somewhere on the ending edge of text block (e.g. bottom-right of last character)
    var z = selectedPoints[2];


    // Rotation of the text block (rotation of its starting, i.e. top or right, edge).
    // 0 - perfectly horizontal, π/2 rad - perfecly vertical
    var theta = a.angleWith(b);

    Direction textDirection = Direction.VERTICAL;
    if (theta < Math.toRadians(45) || theta > Math.toRadians(135)) { // XXX
      textDirection = Direction.HORIZONTAL;
    }

    // Cross section of the text block, perpendicular to the real orientation of the block
    var crossSection = z.distanceFromLine(a, b);
    // x-distance and y-distances from the starting to the ending edge of the block
    var endDeltaX = crossSection;
    var endDeltaY = crossSection;
    var sinTheta = Math.sin(theta);
    var cosTheta = Math.cos(theta);
    switch (textDirection) {
      case VERTICAL -> {
        endDeltaX *= sinTheta;
        endDeltaY *= cosTheta;
      }
      case HORIZONTAL -> {
        endDeltaX *= cosTheta;
        endDeltaY *= sinTheta;
      }
    }
    // End of ending edge of text block (bottom-right of last possible character)
    var c = new Point((int) (b.x() - endDeltaX), (int) (b.y() + endDeltaY));
    // Start of ending edge of text block (top-left or bottom-left of first possible character of
    // last line)
    var d = new Point((int) (a.x() - endDeltaX), (int) (a.y() + endDeltaY));

    // Made up of min. and max. x and y coordinates of points a, b, c, d
    Rectangle ssAreaRect;
    try {
      if (theta <= Math.toRadians(90)) {
        ssAreaRect = Rectangle.ofEdges(d.x(), a.y(), b.x(), c.y());
      } else {
        ssAreaRect = switch (textDirection) {
          case VERTICAL   -> Rectangle.ofEdges(c.x(), d.y(), a.x(), b.y());
          case HORIZONTAL -> Rectangle.ofEdges(a.x(), b.y(), c.x(), d.y());
        };
      }
    } catch (IllegalArgumentException e) {
      recognitionAbandon("Selection incorrect", RecognitionOpError.SELECTION_INCORRECT);
      return;
    }

    var screenshotRes = platform.takeAreaScreenshot(ssAreaRect);
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    var rotation = -theta;
    if (textDirection == Direction.VERTICAL) {
      rotation += Math.toRadians(90);
    }

    var rotated = ImageOps.rotated(screenshotRes.get(), rotation);

    var cropW = crossSection;
    var cropH = a.distanceFrom(b);
    if (textDirection == Direction.HORIZONTAL) {
      var temp = cropW;
      cropW = cropH;
      cropH = temp;
    }

    var ssAreaCenterOwnCoords =
      new Point((ssAreaRect.getWidth() / 2), (ssAreaRect.getHeight() / 2));
    var cropRect = Rectangle.around(ssAreaCenterOwnCoords, (int) cropW, (int) cropH);

    var cropped = ImageOps.cropped(rotated, cropRect);

    doRecognizeBox(cropped, TextOrientation.UNKNOWN);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  public void recognizeAutoBlockDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_FULL);
  }

  public void recognizeAutoBlockColumnDefault(PointSelectionMode mode) {
    recognizeAutoBlock(mode, TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_SINGLE_COLUMN);
  }

  public void recognizeGivenImage(BufferedImage img) {
    LOG.debug("Handling image given recognition request");
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

    doRecognizeAutoBlockGivenImage(screenshotRes.get(), textOrientation, heuristic);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  public void recognizeAutoBlockGivenImage(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic mode
  ) {
    LOG.debug("Handling auto block image recognition request (mode = {})", mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.PROCESSING);
    doRecognizeAutoBlockGivenImage(img, textOrientation, mode);
    updateAndSendRecognizerStatusFn.accept(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeAutoBlockGivenImage(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic heuristic
  ) {
    var maybeBlockImg = recognizer.autoNarrowToTextBlock(img, heuristic);
    if (maybeBlockImg.isEmpty()) {
      var msg = "Text block detection has failed";
      notifyUserOfErrorFn.accept(msg);
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

