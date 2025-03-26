package io.github.kamitejp.recognition;

import static java.util.stream.Collectors.joining;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;
import io.github.kamitejp.recognition.Recognizer.LabelledTesseractHOCROutput;
import io.github.kamitejp.util.Executor;
import io.github.kamitejp.util.Result;
import io.github.kamitejp.util.Strings;

public class TesseractAdapter implements OCRAdapter<OCRAdapterOCRParams.Tesseract> {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String DPI = "70";
  private static final String OEM = "1";
  private static final Map<String, String> ENV = Map.of("OMP_THREAD_LIMIT", "1");
  private static final int PROCESS_TIMEOUT_MS = 5000;

  // Parameters for the flood fill operation used in some cases for background removal
  private static final int BG_REMOVAL_FLOODFILL_NUM_EDGE_FLOOD_POINTS = 24;
  private static final int BG_REMOVAL_FLOODFILL_THRESHOLD = 90;

  // Size of the border added around the input image to create the `white-border` OCR image variant
  private static final int WITH_BORDER_VARIANT_WHITE_BORDER_SIZE = 10;

  @Override
  public Result<BoxRecognitionOutput, LocalOCRError> recognize(
    BufferedImage img,
    OCRAdapterOCRParams.Tesseract params
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
      // XXX
      // if (debug) {
      //   sendDebugImage(img);
      // }
    }

    var hasAltModel = !Strings.isNullOrEmpty(params.modelAlt());

    var tesseractCallables = new ArrayList<Callable<LabelledTesseractResult>>();

    // Queue OCR on the initial screenshot
    final var initial = img;
    tesseractCallables.add(() ->
      new LabelledTesseractResult(
        "initial", doRecognize(initial, TesseractModelType.DEFAULT, params)
      )
    );

    // Queue OCR on the initial screenshot using the alternative model
    if (hasAltModel) {
      tesseractCallables.add(() ->
        new LabelledTesseractResult(
          "initial-alt", doRecognize(initial, TesseractModelType.ALT, params)
        )
      );
    }

    // Invert the image and queue OCR again if we suspect it's white on black
    if (ImageOps.isDarkDominated(img)) {
      ImageOps.negate(img);
      final var negated = img;
      tesseractCallables.add(() ->
        new LabelledTesseractResult(
          "inverted", doRecognize(negated, TesseractModelType.DEFAULT, params)
        )
      );

      if (hasAltModel) {
        tesseractCallables.add(() ->
          new LabelledTesseractResult(
            "inverted-alt", doRecognize(negated, TesseractModelType.ALT, params)
          )
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
      new LabelledTesseractResult(
        "white-border", doRecognize(withBorder, TesseractModelType.DEFAULT, params)
      )
    );

    // Queue OCR on a downscaled version
    final var downscaled = ImageOps.scaled(img, 0.75f);
    tesseractCallables.add(() ->
      new LabelledTesseractResult(
        "downscaled", doRecognize(downscaled, TesseractModelType.DEFAULT, params)
      )
    );

    // Queue OCR on a version with thinner lines
    final var thinLines = ImageOps.copied(img);
    ImageOps.threshold(thinLines, 70, 150);
    tesseractCallables.add(() ->
      new LabelledTesseractResult(
        "thin-lines", doRecognize(thinLines, TesseractModelType.DEFAULT, params)
      )
    );

    // Queue OCR on a blurred version
    final var blurred = ImageOps.blurred(ImageOps.copied(img), /* blurFactor */ 2);
    tesseractCallables.add(() ->
      new LabelledTesseractResult(
        "blurred", doRecognize(blurred, TesseractModelType.DEFAULT, params)
      )
    );

    // Queue OCR on a sharpened version
    final var sharpened = ImageOps.copied(img);
    ImageOps.sharpen(sharpened, /* amount */ 2f, /* threshold */ 0, /* blurFactor */ 3);
    tesseractCallables.add(() ->
      new LabelledTesseractResult(
        "sharpened", doRecognize(sharpened, TesseractModelType.DEFAULT, params)
      )
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
      // XXX: Move logging above?
      LOG.debug("All of the Tesseract calls have failed");
      return Result.Err(new LocalOCRError.Other("All of the Tesseract calls have failed"));
    }

    var parsedVariants = UnprocessedChunkVariants.fromLabelledTesseractHOCROutputs(variants);
    // XXX: Move above
    //if (parsedVariants.isEmpty()) {
    //  return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    //}

    parsedVariants.deduplicate();
    parsedVariants.sortByScore();

    return Result.Ok(new BoxRecognitionOutput(parsedVariants));
  }

  private TesseractResult doRecognize(BufferedImage img, String binPath, String lang, int psm) {
    var imgOS = ImageOps.encodeIntoByteArrayOutputStream(img);
    var res = ProcessHelper.run(
      ProcessRunParams.ofCmd(
        binPath,
        "stdin", "stdout",
        "-l", lang,
        "--dpi", DPI,
        "--oem", OEM,
        "--psm", Integer.toString(psm),
        "-c", "tessedit_create_hocr=1",
        "-c", "hocr_font-info=0"
      )
        .withEnv(ENV)
        .withInputBytes(imgOS.toByteArray())
        .withTimeout(PROCESS_TIMEOUT_MS)
    );
    if (res.didCompleteWithoutError()) {
      return new TesseractResult.HOCR(res.getStdout());
    } else if (res.didCompleteWithError()) {
      return new TesseractResult.Error(res.getStderr());
    } else if (res.didTimeOut()) {
      return new TesseractResult.TimedOut();
    } else {
      return new TesseractResult.ExecutionFailed();
    }
  }

  private record LabelledTesseractResult(String label, TesseractResult result) {}

  private TesseractResult doRecognize(
    BufferedImage img,
    TesseractModelType modelType,
    OCRAdapterOCRParams.Tesseract params
  ) {
    return switch (modelType) {
      case DEFAULT ->
        doRecognize(
          img,
          params.binPath(),
          params.model(),
          params.psm()
        );
      case ALT ->
        doRecognize(
          img,
          params.binPath(),
          params.modelAlt(),
          params.psmAlt() != null ? params.psmAlt() : params.psm()
        );
    };
  }
}
