package io.github.kamitejp.recognition.configuration;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.MangaOCREvent;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.RecognitionOpError;
import io.github.kamitejp.util.Result;

public final class MangaOCROCRConfiguration extends OCRConfiguration<OCRAdapterInitParams.MangaOCR> {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String pythonPath;

  private MangaOCRController adapter = null;

  public MangaOCROCRConfiguration(OCR.Configuration config) {
    super(config);
    pythonPath = config.pythonPath();
    adapterInitParams = new OCRAdapterInitParams.MangaOCR(pythonPath);
  }

  public void initAdapter(Platform platform, Consumer<MangaOCREvent> eventCb) {
    adapter = new MangaOCRController(platform, pythonPath, eventCb);
  }

  public Result<BoxRecognitionOutput, RecognitionOpError> recognizeBox(BufferedImage img) {
    var maybeText = adapter.recognize(img);
    if (maybeText.isEmpty()) {
      return Result.Err(RecognitionOpError.OCR_ERROR);
    }
    var text = maybeText.get();
    if (text.isBlank()) {
      return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    }
    return Result.Ok(new BoxRecognitionOutput(UnprocessedChunkVariants.singleFromString(text)));
  }
}
