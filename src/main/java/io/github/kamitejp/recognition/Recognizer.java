package io.github.kamitejp.recognition;

import static java.util.stream.Collectors.toList;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.MangaOCREvent;
import io.github.kamitejp.platform.MangaOCRInitializationException;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractModel;
import io.github.kamitejp.platform.dependencies.tesseract.TesseractResult;
import io.github.kamitejp.util.Result;

public class Recognizer {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

  private final Platform platform;
  private final OCREngine engine;
  private final boolean debug;
  private final Consumer<RecognizerEvent> eventCb;

  public Recognizer(
    Platform platform,
    OCREngine uninitializedEngine,
    boolean debug,
    Consumer<RecognizerEvent> eventCb
  ) throws RecognizerInitializationException {
    this.platform = platform;
    this.debug = debug;
    this.eventCb = eventCb;

    this.engine = switch (uninitializedEngine) {
      case OCREngine.Tesseract engine ->
        engine;
      case OCREngine.MangaOCR engine -> {
        try {
          yield engine.initialized(platform, this::handleMangaOCREvent);
        } catch (MangaOCRInitializationException e) {
          throw new RecognizerInitializationException( // NOPMD
            "Could not initialize “Manga OCR”: %s".formatted(e.getMessage())
          );
        }
      }
      case OCREngine.OCRSpace engine ->
        engine.initialized();
      case OCREngine.None engine ->
        engine;
    };

    eventCb.accept(new RecognizerEvent.Initialized(getAvailableCommands()));
    LOG.info("Initialized recognizer (engine: {})", uninitializedEngine.displayName());
  }

  public void destroy() {
    engine.destroy();
  }

  private record LabelledTesseractResult(String label, TesseractResult result) {
    public LabelledTesseractHOCROutput asNullableLabelledHOCROutput() {
      if (result instanceof TesseractResult.HOCR hocr) {
        return new LabelledTesseractHOCROutput(label, hocr.hocr());
      }
      return null;
    }
  }

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
      case OCREngine.Tesseract ignored -> recognizeBoxTesseract(img, textOrientation);
      case OCREngine.MangaOCR engine   -> recognizeBoxMangaOCR(engine.controller(), img);
      case OCREngine.OCRSpace engine   -> recognizeBoxOCRSpace(engine.adapter(), img);
      case OCREngine.None ignored      -> Result.Err(RecognitionOpError.OCR_UNAVAILABLE);
    };
  }

  public Optional<BufferedImage> grabAutoBlock(BufferedImage img, AutoBlockHeuristic heuristic) {
    LOG.debug("Grabbing auto block");
    return switch (heuristic) {
      case GAME_TEXTBOX        -> grabAutoBlockGameTextbox(img);
      case MANGA_FULL          -> grabAutoBlockManga(img, false);
      case MANGA_SINGLE_COLUMN -> grabAutoBlockManga(img, true);
    };
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

    // POLISH: Collect error results to a second list and handle them properly
    var variants = tesseractResultFutures.stream()
      .map(CompletableFuture::join)
      .map(LabelledTesseractResult::asNullableLabelledHOCROutput)
      .filter(Objects::nonNull)
      .toList();

    if (variants.isEmpty()) {
      LOG.debug("All of the Tesseract calls have failed");
      return Result.Err(RecognitionOpError.OCR_ERROR);
    } else if (variants.size() < tesseractResultFutures.size()) {
      // POLISH: Distinguish timed out (process.timeoutElapsed())
      LOG.debug("Some of the Tesseract calls have failed");
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

    // DEV: Doesn't work for white-on-black
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

      var top = Integer.MAX_VALUE;
      var bottom = Integer.MIN_VALUE;
      var left = Integer.MAX_VALUE;
      var right = Integer.MIN_VALUE;
      for (var r : rectsOfCurrLine) {
        if (r.getLeft() < left) {
          left = r.getLeft();
        }
        if (r.getTop() < top) {
          top = r.getTop();
        }
        if (r.getRight() > right) {
          right = r.getRight();
        }
        if (r.getBottom() > bottom) {
          bottom = r.getBottom();
        }
      }

      lineRects.add(Rectangle.ofEdges(left, top, right, bottom));
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

  private Optional<BufferedImage> grabAutoBlockManga(BufferedImage img, boolean singleColumn) {
    // Remove alpha channel
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
      img = ImageOps.copied(img, BufferedImage.TYPE_INT_RGB);
    }

    var center = new Point(img.getWidth() / 2, img.getHeight() / 2);

    var gray = ImageOps.copied(img);
    ImageOps.toGrayscale(gray);

    var clean = ImageOps.copied(gray);

    // Invert if light text on dark background suspected
    var darkCheckAreaDim = 50;
    if (ImageOps.isDarkDominated(gray, Rectangle.around(center, darkCheckAreaDim))) {
      ImageOps.negate(gray);
    }

    var eroded = ImageOps.eroded(gray, 2, 2);
    var imgArr = ImageOps.toGrayArray(eroded);
    ImageOps.otsuThreshold(imgArr);

    Graphics debugGfx = null;
    if (debug) {
      debugGfx = img.createGraphics();
    }

    // Get connected components (ccs), excluding those that almost certainly aren't characters
    var ccExtractor = new ConnectedComponentExtractor();
    var initialCCs = Arrays.stream(ccExtractor.extract(imgArr, img.getWidth(), img.getHeight()))
      .skip(1)
      .map(ConnectedComponent::rectangle)
      .toList();

    // if (debug) {
    //   debugGfx.setColor(Color.GREEN);
    //   for (var cc : initialCCs) {
    //     debugGfx.drawRect(cc.getLeft() - 3, cc.getTop() - 3, cc.getWidth() + 6, cc.getHeight() + 6);
    //   }
    // }

    var prefilteredCCs = initialCCs.stream()
      .filter(cc ->
        cc.dimensionsWithin(2, 150)
        && (cc.getWidth() > 5 || cc.getHeight() > 5)
        && cc.getArea() < 4000
      )
      .toList();

    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    //   for (var cc : prefilteredCCs) {
    //     debugGfx.drawRect(cc.getLeft() - 2, cc.getTop() - 2, cc.getWidth() + 4, cc.getHeight() + 4);
    //   }
    // }
    // sendDebugImage(img);

    // Find ccs near the user's click point (assumed to be the center of the input image)
    var nearCcs = new ArrayList<Rectangle>();
    for (var cc : prefilteredCCs) {
      var ccCenter = cc.getCenter();
      var dist = Math.sqrt(
        Math.pow(center.x() - ccCenter.x(), 2)
        + Math.pow(center.y() - ccCenter.y(), 2)
      );
      if (dist < 50) {
        nearCcs.add(cc);
      }
    }

    if (nearCcs.isEmpty()) {
      return Optional.empty();
    }

    if (debug) {
      debugGfx.setColor(Color.ORANGE);
      for (var cc : nearCcs) {
        debugGfx.drawRect(cc.getLeft() - 1, cc.getTop() - 1, cc.getWidth() + 2, cc.getHeight() + 2);
      }
    }

    // Find the average dimensions of the near ccs and take them as the dimensions of an exemplar
    // character cc. We assume that the user has clicked somewhere within the text block to be
    // detected, so the near ccs are most likely to represent characters
    var nearWidthTotal = 0;
    var nearHeightTotal = 0;
    for (var cc : nearCcs) {
      nearWidthTotal += cc.getWidth();
      nearHeightTotal += cc.getHeight();
    }
    final var exemplarCCWidth = nearWidthTotal / nearCcs.size();
    final var exemplarCCHeight = nearHeightTotal / nearCcs.size();

    final var maxW = exemplarCCWidth * 3;
    final var maxH = exemplarCCHeight * 4;
    final var maxArea = exemplarCCWidth * exemplarCCHeight * 5;

    var processedCcs = new ArrayList<Rectangle>();
    for (var cc : prefilteredCCs) {
      if (debug) {
        debugGfx.setColor(Color.GRAY);
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }

      // Use our assumed exemplar character cc to weed out ccs that, judging from their dimensions,
      // probably don't represent characters
      if (!cc.widthWithin(2, maxW) || !cc.heightWithin(2, maxH) || cc.getArea() > maxArea) {
        continue;
      }

      // If we're only interested in a single text column, omit the cc if it's too much horizontally
      // displaced relative to the exemplar cc
      var leeway = exemplarCCWidth * 1.5 - cc.getWidth();
      if (leeway < 0) {
        leeway = 0;
      }
      if (
        singleColumn
        && (center.x() < (cc.getLeft() - leeway) || center.x() > (cc.getRight() + leeway))
      ) {
        continue;
      }

      // Prepare coefficients for growing the cc towards the center of the image (the presumed
      // center of the text block)
      var growX = (int) (exemplarCCWidth * 1.45);
      var growY = (int) (exemplarCCHeight / 1.30);

      // Special case hack: こ, に and similar tend to be detected as separate components, so we
      // need to grow them vertically a lot more than in the ordinary case
      var ratio = cc.getRatio();
      if (cc.getHeight() < exemplarCCHeight && ratio > 1.75 && ratio < 3.25) {
        growY = cc.getHeight() * 3;
      }
      // Analogous hack for certain fonts' い, as well as some characters that are overall slender
      if (cc.getWidth() < exemplarCCWidth && ratio > 0.3 && ratio < 0.55) {
        growX = cc.getWidth() * 3;
      }

      var left = cc.getLeft();
      var right = cc.getRight();
      var top = cc.getTop();
      var bottom = cc.getBottom();

      // Grow the cc towards the center of the image, with clamping
      if (center.x() < left) {
        left = left - growX;
        if (left < 0) {
          left = 0;
        }
      } else if (center.x() > right) {
        right = right + growX;
        if (right >= img.getWidth()) {
          right = img.getWidth() - 1;
        }
      }
      if (center.y() < top) {
        top = top - growY;
        if (top < 0) {
          top = 0;
        }
      } else if (center.y() > bottom) {
        bottom = bottom + growY;
        if (bottom >= img.getHeight()) {
          bottom = img.getHeight() - 1;
        }
      }

      var grown = Rectangle.ofEdges(left, top, right, bottom);
      processedCcs.add(grown);

      if (debug) {
        debugGfx.setColor(Color.MAGENTA);
        debugGfx.drawRect(grown.getLeft(), grown.getTop(), grown.getWidth(), grown.getHeight());
      }
    }

    // Fill a mask with the rectangles of the previously filtered and enlarged ccs
    var mask = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
    var maskGfx = mask.createGraphics();
    maskGfx.setColor(Color.BLACK);
    maskGfx.fillRect(0, 0, img.getWidth(), img.getHeight());
    maskGfx.setColor(Color.WHITE);
    for (var b : processedCcs) {
      maskGfx.fillRect(b.getLeft(), b.getTop(), b.getWidth(), b.getHeight());
    }
    maskGfx.dispose();
    // if (debug) {
    //   sendDebugImageFn.accept(mask);
    // }

    // Find the bounding box of the largest contour in the mask image containing the center point.
    // This is the presumed location of our text block
    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = new ContourFinder().find(maskArr, mask.getWidth(), mask.getHeight());
    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle largestContourBBoxContainingCenter = null;
    int largestBBoxArea = 0;
    for (var c : contours) {
      if (c.type == ContourFinder.ContourType.HOLE) {
        continue;
      }
      var xmin = mask.getWidth() - 1;
      var ymin = mask.getHeight() - 1;
      int xmax = 0;
      int ymax = 0;
      for (var p : c.points) {
        if (p.x() < xmin) xmin = p.x(); // NOPMD
        if (p.x() > xmax) xmax = p.x(); // NOPMD
        if (p.y() < ymin) ymin = p.y(); // NOPMD
        if (p.y() > ymax) ymax = p.y(); // NOPMD
        // if (debug) {
        //   gfx.drawRect(p.x(), p.y(), 1, 1);
        // }
      }

      var width = xmax - xmin;
      var height = ymax - ymin;

      if (
        xmin <= center.x() && center.x() <= xmax
        && ymin <= center.y() && center.y() <= ymax
      ) {
        var area = width * height;
        if (area > largestBBoxArea) {
          largestBBoxArea = area;
          largestContourBBoxContainingCenter = Rectangle.ofEdges(xmin, ymin, xmax, ymax);
        }
      }

      if (debug) {
        debugGfx.drawRect(xmin, ymin, width, height);
      }
    }

    // Crop the original image to where we've determined the text block is. This is the final result
    BufferedImage result = null;
    if (largestContourBBoxContainingCenter != null) {
      var c = largestContourBBoxContainingCenter
        .expanded(4)
        .clamped(img.getWidth() - 1, img.getHeight() - 1);

      if (debug) {
        debugGfx.setColor(Color.GREEN);
        debugGfx.drawRect(c.getLeft(), c.getTop(), c.getWidth(), c.getHeight());
      }

      var cropped = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      var croppedGfx = cropped.getGraphics();
      croppedGfx.drawImage(
        clean,
        0, 0, c.getWidth(), c.getHeight(),
        c.getLeft(), c.getTop(), c.getRight(), c.getBottom(), 
        null
      );
      croppedGfx.dispose();
      result = cropped;
    }

    if (debug) {
      debugGfx.dispose();
      sendDebugImage(img);
    }

    return Optional.ofNullable(result);
  }

  // NOTE: This is a temporary stand-in algorithm copy-pasted with few modifications from
  //       `grabAutoBlockGameManga`. It doesn't need to be refactored/deduplicated, since it is to
  //       be replaced in the future.
  private Optional<BufferedImage> grabAutoBlockGameTextbox(BufferedImage img) {
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
      img = ImageOps.copied(img, BufferedImage.TYPE_INT_RGB);
    }

    var startX = img.getWidth() <= 50 ? img.getWidth() - 1 : 50;
    var startY = img.getHeight() <= 10 ? img.getHeight() - 1 : 10;
    var start = new Point(startX, startY);

    var gray = ImageOps.copied(img);
    ImageOps.toGrayscale(gray);

    var clean = ImageOps.copied(gray);

    var darkCheckAreaDim = 50;
    if (ImageOps.isDarkDominated(gray, Rectangle.around(start, darkCheckAreaDim))) {
      ImageOps.negate(gray);
    }

    var eroded = ImageOps.eroded(gray, 2, 2);
    var imgArr = ImageOps.toGrayArray(eroded);
    ImageOps.otsuThreshold(imgArr);

    var ccExtractor = new ConnectedComponentExtractor();
    var ccs = Arrays.stream(ccExtractor.extract(imgArr, img.getWidth(), img.getHeight()))
      .skip(1)
      .map(cc -> cc.rectangle())
      .filter(cc -> cc.dimensionsWithin(2, 150) && cc.getArea() < 4000)
      .toList();

    Graphics debugGfx = null;
    if (debug) {
      debugGfx = img.createGraphics();
    }

    var nearCcs = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      var ccCenter = cc.getCenter();
      var dist = Math.sqrt(
        Math.pow(start.x() - ccCenter.x(), 2)
        + Math.pow(start.y() - ccCenter.y(), 2)
      );
      if (dist < 50) {
        nearCcs.add(cc);
      }
    }

    if (nearCcs.isEmpty()) {
      return Optional.empty();
    }

    if (debug) {
      debugGfx.setColor(Color.PINK);
      for (var cc : nearCcs) {
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }
    }

    var nearWidthTotal = 0;
    var nearHeightTotal = 0;
    for (var cc : nearCcs) {
      nearWidthTotal += cc.getWidth();
      nearHeightTotal += cc.getHeight();
    }
    var nearWidthAvg = nearWidthTotal / nearCcs.size();
    var nearHeightAvg = nearHeightTotal / nearCcs.size();

    var processedCcs = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      if (debug) {
        debugGfx.setColor(Color.GRAY);
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }

      if (
        cc.getWidth() < 5 && cc.getHeight() < 5
        || cc.getWidth() > nearWidthAvg * 3
        || cc.getHeight() > nearHeightAvg * 4 
        || cc.getArea() > nearWidthAvg * nearHeightAvg * 5
      ) {
        continue;
      }

      var growX = (int) (nearWidthAvg * 5);
      var growY = (int) (nearHeightAvg * 1.5);

      var ratio = cc.getRatio();
      if (cc.getHeight() < nearHeightAvg && ratio > 1.75 && ratio < 3.25) {
        growY = cc.getHeight() * 3;
      }

      var left = cc.getLeft();
      var right = cc.getRight();
      var top = cc.getTop();
      var bottom = cc.getBottom();

      if (start.x() < left) {
        left = left - growX;
        if (left < 0) {
          left = 0;
        }
      } else if (start.x() > right) {
        right = right + growX;
        if (right >= img.getWidth()) {
          right = img.getWidth() - 1;
        }
      }

      if (start.y() < top) {
        top = top - growY;
        if (top < 0) {
          top = 0;
        }
      } else if (start.y() > bottom) {
        bottom = bottom + growY;
        if (bottom >= img.getHeight()) {
          bottom = img.getHeight() - 1;
        }
      }

      var grown = Rectangle.ofEdges(left, top, right, bottom);
      processedCcs.add(grown);

      if (debug) {
        debugGfx.setColor(Color.MAGENTA);
        debugGfx.drawRect(grown.getLeft(), grown.getTop(), grown.getWidth(), grown.getHeight());
      }
    }

    var mask = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
    var maskGfx = mask.createGraphics();
    maskGfx.setColor(Color.BLACK);
    maskGfx.fillRect(0, 0, img.getWidth(), img.getHeight());
    maskGfx.setColor(Color.WHITE);
    for (var b : processedCcs) {
      maskGfx.fillRect(b.getLeft(), b.getTop(), b.getWidth(), b.getHeight());
    }
    maskGfx.dispose();
    // if (debug) {
    //   sendDebugImageFn.accept(mask);
    // }

    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = new ContourFinder().find(maskArr, mask.getWidth(), mask.getHeight());
    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle largestContourBBoxContainingCenter = null;
    var largestBBoxArea = 0;
    for (var c : contours) {
      if (c.type == ContourFinder.ContourType.HOLE) {
        continue;
      }
      var xmin = mask.getWidth() - 1;
      var ymin = mask.getHeight() - 1;
      var xmax = 0;
      var ymax = 0;
      for (var p : c.points) {
        if (p.x() < xmin) xmin = p.x(); // NOPMD
        if (p.x() > xmax) xmax = p.x(); // NOPMD
        if (p.y() < ymin) ymin = p.y(); // NOPMD
        if (p.y() > ymax) ymax = p.y(); // NOPMD
        // if (debug) {
        //   gfx.drawRect(p.x(), p.y(), 1, 1);
        // }
      }

      var width = xmax - xmin;
      var height = ymax - ymin;

      if (
        xmin <= start.x() && start.x() <= xmax
        && ymin <= start.y() && start.y() <= ymax
      ) {
        var area = width * height;
        if (area > largestBBoxArea) {
          largestBBoxArea = area;
          largestContourBBoxContainingCenter = Rectangle.ofEdges(xmin, ymin, xmax, ymax);
        }
      }

      if (debug) {
        debugGfx.drawRect(xmin, ymin, width, height);
      }
    }

    BufferedImage result = null;
    if (largestContourBBoxContainingCenter != null) {
      var c = largestContourBBoxContainingCenter
        .expanded(2)
        .clamped(img.getWidth() - 1, img.getHeight() - 1);

      if (debug) {
        debugGfx.setColor(Color.GREEN);
        debugGfx.drawRect(c.getLeft(), c.getTop(), c.getWidth(), c.getHeight());
      }

      var cropped = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      var croppedGfx = cropped.getGraphics();
      croppedGfx.drawImage(
        clean,
        0, 0, c.getWidth(), c.getHeight(),
        c.getLeft(), c.getTop(), c.getRight(), c.getBottom(), 
        null
      );
      croppedGfx.dispose();
      result = cropped;
    }

    if (debug) {
      debugGfx.dispose();
      sendDebugImage(img);
    }

    return Optional.ofNullable(result);
  }

  private List<String> getAvailableCommands() {
    if (engine instanceof OCREngine.MangaOCR || engine instanceof OCREngine.OCRSpace) {
      return List.of("ocr_manual-block", "ocr_auto-block", "ocr_region");
    } else if (engine instanceof OCREngine.Tesseract) {
      return List.of(
        "ocr_manual-block-vertical",
        "ocr_manual-block-horizontal",
        "ocr_auto-block",
        "ocr_region"
      );
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

  private void sendDebugImage(BufferedImage image) {
    eventCb.accept(new RecognizerEvent.DebugImageSubmitted(image));
  }
}
