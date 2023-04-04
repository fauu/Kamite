package io.github.kamitejp.recognition.configuration;

import static java.util.stream.Collectors.joining;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;
import io.github.kamitejp.recognition.RecognitionOpError;
import io.github.kamitejp.recognition.Recognizer.LabelledTesseractHOCROutput;
import io.github.kamitejp.recognition.TesseractModelType;
import io.github.kamitejp.recognition.TesseractResult;
import io.github.kamitejp.util.Executor;
import io.github.kamitejp.util.Result;

public final class TesseractOCRConfiguration extends OCRConfiguration<OCREngine.Tesseract, OCREngineParams.Tesseract> {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String DEFAULT_BIN_PATH = "tesseract";

  // Parameters for the flood fill operation used in some cases for background removal
  private static final int BG_REMOVAL_FLOODFILL_NUM_EDGE_FLOOD_POINTS = 24;
  private static final int BG_REMOVAL_FLOODFILL_THRESHOLD = 90;

  // Size of the border added around the input image to create the `white-border` OCR image variant
  private static final int WITH_BORDER_VARIANT_WHITE_BORDER_SIZE = 10;

  private final String model;
  private final int psm;
  private final String modelAlt;
  private final Integer psmAlt;

  public TesseractOCRConfiguration(OCR.Configuration config) {
    super(config);
    var binPath = config.path() != null ? config.path() : DEFAULT_BIN_PATH;
    engineInitParams = new OCREngineParams.Tesseract(binPath);
    model = config.tesseractModel();
    psm = config.tesseractPSM();
    modelAlt = config.tesseractModelAlt();
    psmAlt = config.tesseractPSMAlt();
  }

  public boolean hasAltModel() {
    return modelAlt != null;
  }

  private record LabelledTesseractResult(String label, TesseractResult result) {}

  public Result<BoxRecognitionOutput, RecognitionOpError> recognizeBox(BufferedImage img) {
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
      // XXX
      // if (debug) {
      //   sendDebugImage(img);
      // }
    }

    var tesseractCallables = new ArrayList<Callable<LabelledTesseractResult>>();

    // Queue OCR on the initial screenshot
    final var initial = img;
    tesseractCallables.add(() ->
      new LabelledTesseractResult("initial", ocr(initial, TesseractModelType.DEFAULT))
    );

    // Queue OCR on the initial screenshot using the alternative model
    if (hasAltModel()) {
      tesseractCallables.add(() ->
        new LabelledTesseractResult("initial-alt", ocr(initial, TesseractModelType.ALT))
      );
    }

    // Invert the image and queue OCR again if we suspect it's white on black
    if (ImageOps.isDarkDominated(img)) {
      ImageOps.negate(img);
      final var negated = img;
      tesseractCallables.add(() ->
        new LabelledTesseractResult("inverted", ocr(negated, TesseractModelType.DEFAULT))
      );

      if (hasAltModel()) {
        tesseractCallables.add(() ->
          new LabelledTesseractResult("inverted-alt", ocr(negated, TesseractModelType.ALT))
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
      new LabelledTesseractResult("white-border", ocr(withBorder, TesseractModelType.DEFAULT))
    );

    // Queue OCR on a downscaled version
    final var downscaled = ImageOps.scaled(img, 0.75f);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("downscaled", ocr(downscaled, TesseractModelType.DEFAULT))
    );

    // Queue OCR on a version with thinner lines
    final var thinLines = ImageOps.copied(img);
    ImageOps.threshold(thinLines, 70, 150);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("thin-lines", ocr(thinLines, TesseractModelType.DEFAULT))
    );

    // Queue OCR on a blurred version
    final var blurred = ImageOps.blurred(ImageOps.copied(img), /* blurFactor */ 2);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("blurred", ocr(blurred, TesseractModelType.DEFAULT))
    );

    // Queue OCR on a sharpened version
    final var sharpened = ImageOps.copied(img);
    ImageOps.sharpen(sharpened, /* amount */ 2f, /* threshold */ 0, /* blurFactor */ 3);
    tesseractCallables.add(() ->
      new LabelledTesseractResult("sharpened", ocr(sharpened, TesseractModelType.DEFAULT))
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
    ArrayList<String> errorMsgs = null;
    ArrayList<LabelledTesseractHOCROutput> variants = null;
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

    var parsedVariants = UnprocessedChunkVariants.fromLabelledTesseractHOCROutputs(variants);
    if (parsedVariants.isEmpty()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }

    parsedVariants.deduplicate();
    parsedVariants.sortByScore();

    return Result.Ok(new BoxRecognitionOutput(parsedVariants));
  }


  private TesseractResult ocr(BufferedImage img, TesseractModelType modelType) {
    var engine = getEngine();
    var adapter = engine.getAdapter();
    // XXX: This is a mess
    return switch (modelType) {
      case DEFAULT -> adapter.ocr(img, engine.binPath, model, psm);
      case ALT     -> adapter.ocr(img, engine.binPath, modelAlt, psmAlt != null ? psmAlt : psm);
    };
  }
}
