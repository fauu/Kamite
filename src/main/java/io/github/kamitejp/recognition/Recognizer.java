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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractModel;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractResult;
import io.github.kamitejp.recognition.imagefeature.ConnectedComponent;
import io.github.kamitejp.recognition.imagefeature.ConnectedComponentExtractor;
import io.github.kamitejp.util.Result;

public class Recognizer {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  // Width and height of the area around the user's cursor used for auto block recognition
  // ROBUSTNESS: Should probably depend on the screen resolution
  public static final Dimension AUTO_BLOCK_AREA_SIZE = new Dimension(550, 900);

  // Minimum dimension size allowed for box recognition input image
  private static final int BOX_RECOGNITION_INPUT_MIN_DIMENSION = 16;

  // Size of the border added around the input image to create the `white-border` OCR image variant
  private static final int WITH_BORDER_VARIANT_WHITE_BORDER_SIZE = 10;

  // Parameters for the flood fill operation used in some cases for background removal
  private static final int BG_REMOVAL_FLOODFILL_NUM_EDGE_FLOOD_POINTS = 24;
  private static final int BG_REMOVAL_FLOODFILL_THRESHOLD = 90;

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
      case OCREngine.None engine ->
        engine;
    };

    eventCb.accept(new RecognizerEvent.Initialized(getAvailableCommands()));
    LOG.info("Initialized recognizer (engine: {})", () -> uninitializedEngine.displayName());
  }

  public void destroy() {
    engine.destroy();
  }

  private record LabelledTesseractResult(String label, TesseractResult result) {}

  public record LabelledTesseractHOCROutput(String label, String hocr) {}

  public record BoxRecognitionOutput(ChunkVariants chunkVariants) {}

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
    LOG.debug("Starting box recognition");
    return switch (engine) {
      case OCREngine.Tesseract ignored     -> recognizeBoxTesseract(img, textOrientation);
      case OCREngine.MangaOCR engine       -> recognizeBoxMangaOCR(engine.controller(), img);
      case OCREngine.MangaOCROnline engine -> recognizeBoxMangaOCROnline(engine.adapter(), img);
      case OCREngine.OCRSpace engine       -> recognizeBoxOCRSpace(engine.adapter(), img);
      case OCREngine.None ignored          -> Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
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
    return Result.Ok(new BoxRecognitionOutput(ChunkVariants.singleFromString(text)));
  }

  private static Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxMangaOCROnline(
    MangaOCRGGAdapter adapter,
    BufferedImage img
  ) {
    var res = adapter.ocr(img);
    if (res.isErr()) {
      LOG.error("\"Manga OCR\" Online error: {}", res.err());
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }

    var text = res.get();
    if (text.isBlank()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    return Result.Ok(new BoxRecognitionOutput(ChunkVariants.singleFromString(text)));
  }

  private static Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxOCRSpace(
    OCRSpaceAdapter adapter,
    BufferedImage img
  ) {
    var res = adapter.ocr(ImageOps.encodeIntoByteArrayOutputStream(img).toByteArray());
    if (res.isErr()) {
      LOG.error("OCR.space error: {}", res.err());
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }

    var text = res.get();
    if (text.isBlank()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    return Result.Ok(new BoxRecognitionOutput(ChunkVariants.singleFromString(text)));
  }

  private Result<BoxRecognitionOutput, RecognitionOpError> recognizeBoxTesseract(
    BufferedImage img, TextOrientation textOrientation
  ) {
    // Remove alpha channel
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
      img = ImageOps.copied(img, BufferedImage.TYPE_INT_RGB);
    }

    var tesseractResultFutures = new ArrayList<CompletableFuture<LabelledTesseractResult>>();

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

    // Queue OCR on the initial screenshot
    final var initial = img;
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("initial", platform.tesseractOCR(initial, model))
    ));

    // Queue OCR on the initial screenshot using the alternative model
    if (altModel != null) {
      tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
        new LabelledTesseractResult("initial-alt", platform.tesseractOCR(initial, altModel))
      ));
    }

    // Invert the image and queue OCR again if we suspect it's white on black
    if (ImageOps.isDarkDominated(img)) {
      ImageOps.negate(img);
      final var negated = img;
      tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
        new LabelledTesseractResult("inverted", platform.tesseractOCR(negated, model))
      ));

      if (altModel != null) {
        tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
          new LabelledTesseractResult("inverted-alt", platform.tesseractOCR(negated, altModel))
        ));
      }
    }

    // if (debug) {
    //   detectTextLines(img);
    // }

    // Queue OCR on a version with a white border
    final var withBorder = ImageOps.withBorder(
      ImageOps.copied(img),
      Color.WHITE,
      WITH_BORDER_VARIANT_WHITE_BORDER_SIZE
    );
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("white-border", platform.tesseractOCR(withBorder, model))
    ));

    // Queue OCR on a downscaled version
    final var downscaled = ImageOps.scaled(img, 0.75f);
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("downscaled", platform.tesseractOCR(downscaled, model))
    ));

    // Queue OCR on a version with thinner lines
    final var thinLines = ImageOps.copied(img);
    ImageOps.threshold(thinLines, 70, 150);
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("thin-lines", platform.tesseractOCR(thinLines, model))
    ));

    // Queue OCR on a blurred version
    final var blurred = ImageOps.blurred(ImageOps.copied(img), /* blurFactor */ 2);
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("blurred", platform.tesseractOCR(blurred, model))
    ));

    // Queue OCR on a sharpened version
    final var sharpened = ImageOps.copied(img);
    ImageOps.sharpen(sharpened, /* amount */ 2f, /* threshold */ 0, /* blurFactor */ 3);
    tesseractResultFutures.add(CompletableFuture.supplyAsync(() ->
      new LabelledTesseractResult("sharpened", platform.tesseractOCR(sharpened, model))
    ));

    // Run the queued operations
    CompletableFuture.allOf(tesseractResultFutures.toArray(new CompletableFuture[0])).join();

    // Transform the results
    var numExecutions = tesseractResultFutures.size();
    var numExecutionFails = 0;
    var numTimeouts = 0;
    ArrayList<String> errorMsgs = null;
    ArrayList<LabelledTesseractHOCROutput> variants = null;
    for (var labelledResultFuture : tesseractResultFutures) {
      var labelledResult = labelledResultFuture.join();
      switch (labelledResult.result) { // NOPMD - misidentifies as non-exhaustive
        case TesseractResult.ExecutionFailed ignored ->
          numExecutionFails++;
        case TesseractResult.TimedOut ignored ->
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

    var parsedVariants = ChunkVariants.fromLabelledTesseractHOCROutputs(variants);
    if (parsedVariants.isEmpty()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    parsedVariants.deduplicate();
    parsedVariants.sortByScore();

    return Result.Ok(new BoxRecognitionOutput(parsedVariants));
  }

  private static class LineBucket {
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
        "ocr_region"
      );
    } else if (!(engine instanceof OCREngine.None)) {
      return List.of("ocr_manual-block", "ocr_auto-block", "ocr_region");
    } else {
      return List.of();
    }
  }

  private void handleMangaOCREvent(MangaOCREvent event) {
    var transformedEvent = switch (event) {
      case MangaOCREvent.Started ignored ->
        null;
      case MangaOCREvent.StartedDownloadingModel ignored ->
        new RecognizerEvent.MangaOCRStartedDownloadingModel();
      case MangaOCREvent.Crashed ignored ->
        new RecognizerEvent.Crashed();
      case MangaOCREvent.TimedOutAndRestarting ignored ->
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
