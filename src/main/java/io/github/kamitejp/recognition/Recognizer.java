package io.github.kamitejp.recognition;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.MangaOCREvent;
import io.github.kamitejp.platform.MangaOCRInitializationException;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractModel;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractResult;
import io.github.kamitejp.recognition.imagefeature.ConnectedComponent;
import io.github.kamitejp.recognition.imagefeature.ConnectedComponentExtractor;
import io.github.kamitejp.util.Executor;
import io.github.kamitejp.util.Maths;
import io.github.kamitejp.util.Result;

public class Recognizer {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  // Width and height of the area around the user's cursor used for auto block recognition
  // ROBUSTNESS: Should probably depend on the screen resolution
  public static final Dimension AUTO_BLOCK_AREA_SIZE = new Dimension(550, 900);

  // How many seconds to wait before each retry of remote OCR request.
  private static final List<Integer> REMOTE_OCR_RETRY_INTERVALS_MS = List.of(4000, 7000);
  private static final int REMOTE_OCR_MAX_ATTEMPTS = REMOTE_OCR_RETRY_INTERVALS_MS.size() + 1;

  // Minimum dimension size allowed for box recognition input image
  private static final int BOX_RECOGNITION_INPUT_MIN_DIMENSION = 16;

  // Size of the border added around the input image to create the `white-border` OCR image variant
  private static final int WITH_BORDER_VARIANT_WHITE_BORDER_SIZE = 10;

  // Parameters for the flood fill operation used in some cases for background removal
  private static final int BG_REMOVAL_FLOODFILL_NUM_EDGE_FLOOD_POINTS = 24;
  private static final int BG_REMOVAL_FLOODFILL_THRESHOLD = 90;

  // Values of text block starting edge rotation in radians between which the text block is assumed
  // to be of vertical, rather than horizontal orientation
  private static double ROTATED_TEXT_VERTICAL_THETA_MIN = Maths.DEGREES_45_IN_RADIANS;
  private static double ROTATED_TEXT_VERTICAL_THETA_MAX = 3 * Maths.DEGREES_45_IN_RADIANS;
  // Size of the margin preserved when cropping a derotated text block. Necessary because the user
  // selection tends to not perfectly correspond to the actual angle of the rotated text block
  private static final int DEROTATED_CROP_SAFETY_MARGIN = 10;

  private static final Point DEBUG_IMAGE_LABEL_ORIGIN = new Point(3, 3);
  private static final Color DEBUG_IMAGE_LABEL_OUTLINE_COLOR = Color.BLACK;
  private static final Color DEBUG_IMAGE_LABEL_FILL_COLOR = Color.WHITE;
  private static final Stroke DEBUG_IMAGE_LABEL_STROKE =
    new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

  private final Platform platform;
  private final OCREngine engine;
  private final boolean debug;
  private final Consumer<RecognizerEvent> eventCb;
  private final Map<AutoBlockHeuristic, AutoBlockDetector> autoBlockDetectors;

  public Recognizer(
    Platform platform,
    OCREngine uninitializedEngine,
    boolean debug,
    Consumer<RecognizerEvent> eventCb
  ) throws RecognizerInitializationException {
    this.platform = platform;
    this.debug = debug;
    this.eventCb = eventCb;
    this.autoBlockDetectors = new HashMap<>();

    this.engine = switch (uninitializedEngine) {
      case OCREngine.Tesseract engine ->
        engine;
      case OCREngine.MangaOCR engine -> {
        try {
          yield engine.initialized(platform, this::handleMangaOCREvent);
        } catch (MangaOCRInitializationException e) {
          throw new RecognizerInitializationException( // NOPMD
            "Could not initialize \"Manga OCR\": %s".formatted(e.getMessage())
          );
        }
      }
      case OCREngine.MangaOCROnline engine ->
        engine.initialized();
      case OCREngine.OCRSpace engine ->
        engine.initialized();
      case OCREngine.EasyOCROnline engine ->
        engine.initialized();
      case OCREngine.HiveOCROnline engine ->
        engine.initialized();
      case OCREngine.None engine ->
        engine;
    };

    eventCb.accept(new RecognizerEvent.Initialized(getAvailableCommands()));
    LOG.info("Initialized recognizer. Engine: {}", uninitializedEngine::toString);
  }

  public void destroy() {
    engine.destroy();
  }

  private record LabelledTesseractResult(String label, TesseractResult result) {}

  public record LabelledTesseractHOCROutput(String label, String hocr) {}

  public record BoxRecognitionOutput(UnprocessedChunkVariants chunkVariants) {}

  public Result<BoxRecognitionOutput, RecognitionOpError> recognizeBox(
    BufferedImage img,
    TextOrientation textOrientation
  ) {
    if (
      img.getWidth() < BOX_RECOGNITION_INPUT_MIN_DIMENSION
      || img.getHeight() < BOX_RECOGNITION_INPUT_MIN_DIMENSION
    ) {
      return Result.Err(RecognitionOpError.INPUT_TOO_SMALL);
    }

    if (debug) {
      sendDebugImage(img, "%s OCR".formatted(engine.isRemote() ? "Remote" : "Local"));
    }

    LOG.debug("Starting box recognition");
    return switch (engine) {
      case OCREngine.Tesseract _     -> recognizeBoxTesseract(img, textOrientation);
      case OCREngine.MangaOCR engine       -> recognizeBoxMangaOCR(engine.controller(), img);
      case OCREngine.MangaOCROnline engine -> recognizeBoxRemote(engine.adapter(), img);
      case OCREngine.OCRSpace engine       -> recognizeBoxRemote(engine.adapter(), img);
      case OCREngine.EasyOCROnline engine  -> recognizeBoxRemote(engine.adapter(), img);
      case OCREngine.HiveOCROnline engine  -> recognizeBoxRemote(engine.adapter(), img);
      case OCREngine.None _          -> Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    };
  }

  public Optional<BufferedImage> autoNarrowToTextBlock(
    BufferedImage img, AutoBlockHeuristic heuristic
  ) {
    LOG.debug("Detecting a text block");
    var detector = autoBlockDetectors.get(heuristic);
    if (detector == null) {
      detector = AutoBlockDetector.fromHeuristic(heuristic);
    }
    if (detector == null) {
      return Optional.empty();
    }
    var block = detector.detect(img, debug, this::sendDebugImage);
    return block.map(b -> ImageOps.cropped(img, b));
  }

  private enum BlockRotation {
    BELOW_HORIZONTAL, // Edge start below and to the left of edge end
    BELOW_VERTICAL, // Edge start above and to the left of edge end
    ABOVE_VERTICAL // Edge start above and to the right of edge end
  }

  public record RotatedBlockInfo(
    double theta,
    double edgeLength,
    double crossSection,
    TextOrientation textOrientation,
    Rectangle boundingRectangle
  ) {}

  public static Optional<RotatedBlockInfo> computeRotatedBlock(Point[] selectedPoints) {
   /*
    *  ~~ - text in the block
    *  a, b, c, d, z - points defined below
    *  s - the starting edge of the block
    *  e - the ending edge of the block
    *
    *      d     a
    *      e~~ ~~s
    *     e~~ ~~s
    *    e~~ ~~s
    *   ez  ~~s
    *  e   ~~s
    *  c     b
    */

    // Beginning of starting edge of text block in screen coordinates (top-right [vertical text] or
    // top-left [horizontal text] of first possible character)
    var a = selectedPoints[0];
    // End of starting edge of text block (bottom-right or top-right of first possible character)
    var b = selectedPoints[1];
    // Somewhere on the ending edge of text block (e.g. bottom-left or bottom-right of last
    // character)
    var z = selectedPoints[2];

    // Angle of the text block (of its starting, i.e. top or right, edge) to the x axis:
    //   < 0       - slanting left towards top
    //   0         - perfectly horizontal
    //   π/2 rad   - perfecly vertical
    //   > π/2 rad - slanted right
    var theta = a.angleWith(b);

    var edgesDirectedDistance = z.directedDistanceFromLine(a, b);
    if (edgesDirectedDistance <= 0) {
      LOG.debug("The specified rotated block is mirrored: not supported");
      return Optional.empty();
    }

    BlockRotation blockRotation;
    if (theta < -Maths.DEGREES_90_IN_RADIANS) {
      LOG.debug("The specified rotated block is upside down: not supported");
      return Optional.empty();
    } else if (theta < 0) {
      blockRotation = BlockRotation.BELOW_HORIZONTAL;
    } else if (theta < Maths.DEGREES_90_IN_RADIANS) {
      blockRotation = BlockRotation.BELOW_VERTICAL;
    } else {
      blockRotation = BlockRotation.ABOVE_VERTICAL;
    }

    TextOrientation textOrientation = TextOrientation.VERTICAL;
    if (theta < ROTATED_TEXT_VERTICAL_THETA_MIN || theta > ROTATED_TEXT_VERTICAL_THETA_MAX) {
      textOrientation = TextOrientation.HORIZONTAL;
    }

    // x-distance and y-distances from the starting to the ending edge of the block
    var endDeltaX = edgesDirectedDistance;
    var endDeltaY = edgesDirectedDistance;
    switch (blockRotation) { // NOPMD - misidentifies as non-exhaustive
      case BELOW_HORIZONTAL -> {
        var t = -theta;
        endDeltaX *= Math.sin(t);
        endDeltaY *= Math.cos(t);
      }
      case BELOW_VERTICAL -> {
        var t = Maths.DEGREES_90_IN_RADIANS - theta;
        endDeltaX *= -Math.cos(t);
        endDeltaY *= Math.sin(t);
      }
      case ABOVE_VERTICAL -> {
        var t = theta - Maths.DEGREES_90_IN_RADIANS;
        endDeltaX *= -Math.cos(t);
        endDeltaY *= -Math.sin(t);
      }
    }

    // End of ending edge of text block (bottom-left or bottom-right right of last possible
    // character)
    var c = new Point((int) (b.x() + endDeltaX), (int) (b.y() + endDeltaY));
    // Start of ending edge of text block (top-left or bottom-left of first possible character of
    // last line)
    var d = new Point((int) (a.x() + endDeltaX), (int) (a.y() + endDeltaY));

    try {
      // Made up of min. and max. x and y coordinates of points a, b, c, d
      var boundingRectangle = switch (blockRotation) {
        case BELOW_HORIZONTAL -> Rectangle.ofEdges(a.x(), b.y(), c.x(), d.y());
        case BELOW_VERTICAL   -> Rectangle.ofEdges(d.x(), a.y(), b.x(), c.y());
        case ABOVE_VERTICAL   -> Rectangle.ofEdges(c.x(), d.y(), a.x(), b.y());
      };
      return Optional.of(new RotatedBlockInfo(
        theta,
        /* edgeLength */   a.distanceFrom(b),
        /* crossSection */ edgesDirectedDistance,
        textOrientation,
        boundingRectangle
      ));
    } catch (IllegalArgumentException e) {
      // Just in case. Should've been caught earlier when checking invariants
      LOG.error("Undetected incorrect rotated block selection");
      return Optional.empty();
    }
  }

  public static BufferedImage straightenRotatedBlockImage(
    RotatedBlockInfo block, BufferedImage img
  ) {
    var rotation = -block.theta;
    if (block.textOrientation == TextOrientation.VERTICAL) {
      rotation += Maths.DEGREES_90_IN_RADIANS;
    }

    var fillColor = ImageOps.averageEdgeColor(img);

    var rotated = ImageOps.rotated(img, rotation, fillColor);

    var cropW = block.crossSection + DEROTATED_CROP_SAFETY_MARGIN;
    var cropH = block.edgeLength + DEROTATED_CROP_SAFETY_MARGIN;
    if (block.textOrientation == TextOrientation.HORIZONTAL) {
      var temp = cropW;
      cropW = cropH;
      cropH = temp;
    }

    var r = block.boundingRectangle();
    var boundingRectangleCenterOwnCoords = new Point(r.getWidth() / 2, r.getHeight() / 2);
    var cropRect = Rectangle.around(boundingRectangleCenterOwnCoords, (int) cropW, (int) cropH);

    // PERF: Rotate and crop in one go?
    return ImageOps.cropped(rotated, cropRect, fillColor);
  }

  private static Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxMangaOCR(
    MangaOCRController controller,
    BufferedImage img
  ) {
    var maybeText = controller.recognize(img);
    if (maybeText.isEmpty()) {
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }
    var text = maybeText.get();
    if (text.isBlank()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }
    return Result.Ok(new BoxRecognitionOutput(UnprocessedChunkVariants.singleFromString(text)));
  }

  private static Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxRemote(
    RemoteOCRAdapter adapter,
    BufferedImage img
  ) {
    Result<String, RemoteOCRRequestError> res = null;
    var mightAttempt = true;
    for (var attemptNo = 0; mightAttempt && attemptNo < REMOTE_OCR_MAX_ATTEMPTS; attemptNo++) {
      if (attemptNo > 0) {
        try {
          Thread.sleep(REMOTE_OCR_RETRY_INTERVALS_MS.get(attemptNo - 1));
        } catch (InterruptedException e) {
          LOG.debug("Interrupted while waiting to retry remote OCR request");
        }
        LOG.info("Retrying remote OCR request");
      }
      res = adapter.ocr(img);
      mightAttempt = false;
      if (res.isErr()) {
        var msg = switch (res.err()) {
          case RemoteOCRRequestError.Timeout _ -> {
            mightAttempt = true;
            yield "HTTP request timed out";
          }
          case RemoteOCRRequestError.SendFailed err -> {
            mightAttempt = true;
            yield "HTTP client send execution has failed: %s".formatted(err.exceptionMessage());
          }
          case RemoteOCRRequestError.Unauthorized _ ->
            "Received `Unauthorized` response. The provided API key is likely invalid";
          case RemoteOCRRequestError.UnexpectedStatusCode err ->
            "Received unexpected status code: %s".formatted(err.code());
          case RemoteOCRRequestError.Other err ->
            err.error();
        };
        LOG.error("Remote OCR service error: {}", msg);
      }
    }
    if (res.isErr()) {
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }

    var text = res.get();
    if (text.isBlank()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    return Result.Ok(new BoxRecognitionOutput(UnprocessedChunkVariants.singleFromString(text)));
  }

  private Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxTesseract(
    BufferedImage img, TextOrientation textOrientation
  ) {
    // Remove alpha channel
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
      img = ImageOps.copied(img, BufferedImage.TYPE_INT_RGB);
    }

    // If we detect background features that are likely to ruin text detection, try to get rid of
    // them by flood filling the image with white and then applying otsu threshold
    if (!ImageOps.isMostlyColorless(img) || ImageOps.hasBusyEdges(img)) {
      img = ImageOps.withWhiteFloodFilledBackground(
        img,
        BG_REMOVAL_FLOODFILL_NUM_EDGE_FLOOD_POINTS,
        BG_REMOVAL_FLOODFILL_THRESHOLD
      );
      var imgArr = ImageOps.toGrayArray(img);
      ImageOps.otsuThreshold(imgArr);
      img = ImageOps.grayArrayToBufferedImage(imgArr, img.getWidth(), img.getHeight());
      if (debug) {
        sendDebugImage(img);
      }
    }

    // Determine which tesseract models to use
    TesseractModel tmpModel = null;
    TesseractModel tmpAltModel = null;
    switch (textOrientation) { // NOPMD - misidentifies as non-exhaustive
      case VERTICAL, UNKNOWN -> {
        tmpModel = TesseractModel.VERTICAL;
        tmpAltModel = TesseractModel.VERTICAL_ALT;
      }
      case HORIZONTAL ->
        tmpModel = TesseractModel.HORIZONTAL;
    }
    final var model = tmpModel;
    final var altModel = tmpAltModel;

    var tesseractCallables = new ArrayList<Callable<LabelledTesseractResult>>();

    // Queue OCR on the initial screenshot
    final var initial = img;
    tesseractCallables.add(() ->
      new LabelledTesseractResult("initial", platform.tesseractOCR(initial, model))
    );

    // Queue OCR on the initial screenshot using the alternative model
    if (altModel != null) {
      tesseractCallables.add(() ->
        new LabelledTesseractResult("initial-alt", platform.tesseractOCR(initial, altModel))
      );
    }

    // Invert the image and queue OCR again if we suspect it's white on black
    if (ImageOps.isDarkDominated(img)) {
      ImageOps.negate(img);
      final var negated = img;
      tesseractCallables.add(() ->
        new LabelledTesseractResult("inverted", platform.tesseractOCR(negated, model))
      );

      if (altModel != null) {
        tesseractCallables.add(() ->
          new LabelledTesseractResult("inverted-alt", platform.tesseractOCR(negated, altModel))
        );
      }
    }

    // XXX: (DEV)
    // if (debug) {
    //   detectTextLines(img);
    // }

    // Queue OCR on a version with a white border
    final var withBorder = ImageOps.withBorder(
      ImageOps.copied(img),
      Color.WHITE,
      WITH_BORDER_VARIANT_WHITE_BORDER_SIZE
    );
    tesseractCallables.add(() ->
      new LabelledTesseractResult("white-border", platform.tesseractOCR(withBorder, model))
    );

    // Queue OCR on a downscaled version
    final var downscaled = ImageOps.scaled(img, 0.75f);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("downscaled", platform.tesseractOCR(downscaled, model))
    );

    // Queue OCR on a version with thinner lines
    final var thinLines = ImageOps.copied(img);
    ImageOps.threshold(thinLines, 70, 150);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("thin-lines", platform.tesseractOCR(thinLines, model))
    );

    // Queue OCR on a blurred version
    final var blurred = ImageOps.blurred(ImageOps.copied(img), /* blurFactor */ 2);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("blurred", platform.tesseractOCR(blurred, model))
    );

    // Queue OCR on a sharpened version
    final var sharpened = ImageOps.copied(img);
    ImageOps.sharpen(sharpened, /* amount */ 2f, /* threshold */ 0, /* blurFactor */ 3);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("sharpened", platform.tesseractOCR(sharpened, model))
    );

    List<Future<LabelledTesseractResult>> tesseractResultFutures = null;

    try {
      tesseractResultFutures = Executor.get().invokeAll(tesseractCallables);
    } catch (InterruptedException e) {
      LOG.error(e);
    }

    // Transform the results
    var numExecutions = tesseractResultFutures.size();
    var numExecutionFails = 0;
    var numTimeouts = 0;
    List<String> errorMsgs = null;
    List<LabelledTesseractHOCROutput> variants = null;
    for (var labelledResultFuture : tesseractResultFutures) {
      LabelledTesseractResult labelledResult = null;
      try {
        labelledResult = labelledResultFuture.get();
      } catch (ExecutionException | InterruptedException e) {
        LOG.debug("Exception while getting future labelled tesseract result", e);
        numExecutionFails++;
        continue;
      }
      switch (labelledResult.result) { // NOPMD - misidentifies as non-exhaustive
        case TesseractResult.ExecutionFailed _ ->
          numExecutionFails++;
        case TesseractResult.TimedOut _ ->
          numTimeouts++;
        case TesseractResult.Error error -> {
          if (errorMsgs == null) {
            errorMsgs = new ArrayList<>();
          }
          errorMsgs.add(error.error());
        }
        case TesseractResult.HOCR hocr -> {
          if (variants == null) {
            variants = new ArrayList<>();
          }
          variants.add(new LabelledTesseractHOCROutput(labelledResult.label, hocr.hocr()));
        }
      }
    }

    // Handle failures
    if (numExecutionFails > 0) {
      LOG.error(
        "Some of the Tesseract calls have failed to execute ({}/{})",
        numExecutionFails, numExecutions
      );
    }
    if (numTimeouts > 0) {
      LOG.error(
        "Some of the Tesseract calls have timed out ({}/{})",
        numTimeouts, numExecutions
      );
    }
    if (errorMsgs != null) {
      LOG.error( // NOPMD
        "Some of the Tesseract calls have returned errors:\n{}",
        errorMsgs.stream().distinct().collect(joining("\n"))
      );
    }
    if (variants == null) {
      LOG.debug("All of the Tesseract calls have failed");
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }

    var parsedVariants = UnprocessedChunkVariants.fromLabelledTesseractHOCROutputs(variants);
    if (parsedVariants.isEmpty()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    parsedVariants.deduplicate();
    parsedVariants.sortByScore();

    return Result.Ok(new BoxRecognitionOutput(parsedVariants));
  }

  private static final class LineBucket {
    private float avgX = Float.NaN;
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private final List<Rectangle> rects = new ArrayList<>();

    public void add(Rectangle rect) {
      var center = rect.getCenter();
      var n = rects.size();
      avgX = n == 0 ? center.x() : ((avgX * n) + center.x()) / (n + 1); // NOPMD
      minY = Math.min(minY, rect.getTop());
      maxY = Math.max(maxY, rect.getBottom());
      rects.add(rect);
    }

    public List<Rectangle> getRects() {
      return rects;
    }
  }

  // DEV
  @SuppressWarnings("unused")
  private List<Rectangle> detectVerticalTextLines(BufferedImage img) {
    var lineRects = new ArrayList<Rectangle>(16);

    Graphics debugGfx = null;
    BufferedImage debugImg = null;
    if (debug) {
      debugImg = ImageOps.copied(img);
      debugGfx = debugImg.createGraphics();
    }

    // DEV: Doesn't work as expected for white-on-black
    var eroded = ImageOps.eroded(img, 2, 2);
    var imgArr = ImageOps.toGrayArray(eroded);
    ImageOps.otsuThreshold(imgArr);

    var ccExtractor = new ConnectedComponentExtractor();
    final var ccMetrics = new Object() { int totalWidth; int totalHeight; };
    var ccs = Arrays.stream(ccExtractor.extract(imgArr, img.getWidth(), img.getHeight()))
      .skip(1)
      .map(ConnectedComponent::rectangle)
      .filter(cc -> cc.dimensionsWithin(1, 150) && cc.getArea() < 4000)
      .peek(cc -> {
        ccMetrics.totalWidth += cc.getWidth();
        ccMetrics.totalHeight += cc.getHeight();
      })
      .toList();
    if (ccs.isEmpty()) {
      return lineRects;
    }

    var ccAvgWidth = ccMetrics.totalWidth / ccs.size();
    var ccAvgHeight = ccMetrics.totalHeight / ccs.size();

    if (debug) {
      debugGfx.setColor(Color.PINK);
      for (var cc : ccs) {
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }
    }

    var lineToleranceX = ccAvgWidth * 1.5;
    var lineBuckets = new ArrayList<LineBucket>(16);
    for (var cc : ccs) {
      LineBucket targetBucket = null;
      var xDistToTargetBucket = Float.POSITIVE_INFINITY;
      for (var b : lineBuckets) {
        var xDistToB = Math.abs(b.avgX - cc.getCenter().x());
        if (xDistToB <= lineToleranceX && xDistToB < xDistToTargetBucket) {
          xDistToTargetBucket = xDistToB;
          targetBucket = b;
        }
      }
      if (targetBucket == null) {
        targetBucket = new LineBucket();
        lineBuckets.add(targetBucket);
      }
      targetBucket.add(cc);
    }

    var lineToleranceY = ccAvgHeight * 2;
    for (var b : lineBuckets) {
      var discontinuityIndices = Stream.of(0).collect(toList());
      var rects = b.getRects();
      rects.sort(Comparator.comparingInt(Rectangle::getTop));
      for (int i = 1; i < rects.size(); i++) {
        if (rects.get(i).getTop() - rects.get(i - 1).getBottom() > lineToleranceY) {
          discontinuityIndices.add(i);
        }
      }
      discontinuityIndices.add(rects.size());

      var longestContinuityStartIdx = -1;
      var longestContinuitySize = Integer.MIN_VALUE;
      for (int i = 1; i < discontinuityIndices.size(); i++) {
        var prev = discontinuityIndices.get(i - 1);
        var prevContinuitySize = discontinuityIndices.get(i) - prev;
        if (prevContinuitySize > longestContinuitySize) {
          longestContinuityStartIdx = prev;
          longestContinuitySize = prevContinuitySize;
        }
      }

      var rectsOfCurrLine = rects.subList(
        longestContinuityStartIdx,
        longestContinuityStartIdx + longestContinuitySize
      );
      lineRects.add(Rectangle.around(rectsOfCurrLine));
    }

    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    for (var r : lineRects) {
      if (debug) {
        debugGfx.drawRect(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight());
      }
    }

    sendDebugImage(debugImg);

    if (debug) {
      debugGfx.dispose();
    }

    return lineRects;
  }

  private List<String> getAvailableCommands() {
    if (platform.getUnsupportedFeatures().contains(PlatformDependentFeature.GLOBAL_OCR)) {
      return List.of();
    } else if (engine instanceof OCREngine.Tesseract) {
      return List.of(
        "ocr_manual-block-vertical",
        "ocr_manual-block-horizontal",
        "ocr_auto-block",
        "ocr_manual-block-rotated",
        "ocr_region"
      );
    } else if (!(engine instanceof OCREngine.None)) {
      return List.of(
        "ocr_manual-block",
        "ocr_auto-block",
        "ocr_manual-block-rotated",
        "ocr_region"
      );
    } else {
      return List.of();
    }
  }

  private void handleMangaOCREvent(MangaOCREvent event) {
    var transformedEvent = switch (event) {
      case MangaOCREvent.Started _ ->
        null;
      case MangaOCREvent.StartedDownloadingModel _ ->
        new RecognizerEvent.MangaOCRStartedDownloadingModel();
      case MangaOCREvent.Crashed _ ->
        new RecognizerEvent.Crashed();
      case MangaOCREvent.TimedOutAndRestarting _ ->
        new RecognizerEvent.Restarting(RecognizerRestartReason.MANGA_OCR_TIMED_OUT_AND_RESTARTING);
    };
    if (transformedEvent != null) {
      eventCb.accept(transformedEvent);
    }
  }

  private void sendDebugImage(BufferedImage image, String label) {
    var copied = ImageOps.copied(image);
    var gfx = copied.getGraphics();
    drawDebugLabel(gfx, label);
    gfx.dispose();
    sendDebugImage(copied);
  }

  private void sendDebugImage(BufferedImage image) {
    eventCb.accept(new RecognizerEvent.DebugImageSubmitted(image));
  }

  // https://stackoverflow.com/a/35222059/2498764
  private void drawDebugLabel(Graphics gfx, String text) {
    var gfx2d = (Graphics2D) gfx;

    var originalStroke = gfx2d.getStroke();
    var originalHints = gfx2d.getRenderingHints();

    var glyphVector = gfx.getFont().createGlyphVector(gfx2d.getFontRenderContext(), text);
    var textShape = glyphVector.getOutline();

    gfx2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    gfx2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

    gfx2d.setColor(DEBUG_IMAGE_LABEL_OUTLINE_COLOR);
    gfx2d.setStroke(DEBUG_IMAGE_LABEL_STROKE);
    gfx2d.translate(
      DEBUG_IMAGE_LABEL_ORIGIN.x(),
      DEBUG_IMAGE_LABEL_ORIGIN.y() + gfx.getFontMetrics().getAscent()
    );
    gfx2d.draw(textShape);
    gfx2d.setColor(DEBUG_IMAGE_LABEL_FILL_COLOR);
    gfx2d.fill(textShape);

    gfx2d.setStroke(originalStroke);
    gfx2d.setRenderingHints(originalHints);
  }

}
