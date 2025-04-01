package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;
import io.github.kamitejp.recognition.adapter.TesseractAdapter;

public final class TesseractOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.Tesseract,
        TesseractAdapter> {
  private static final String DEFAULT_BIN_PATH = "tesseract";

  public TesseractOcrConfiguration(Ocr.Configuration config) {
    super(config);
    adapterInitParams = new OcrAdapterInitParams.Empty();
    adapterOcrParams = new OcrAdapterOcrParams.Tesseract(
      config.binPath() != null ? config.binPath() : DEFAULT_BIN_PATH,
      config.model(),
      config.psm(),
      config.modelAlt(),
      config.psmAlt()
    );
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new TesseractAdapter();
  }
}
