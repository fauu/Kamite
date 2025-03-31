package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;
import io.github.kamitejp.recognition.TesseractAdapter;

public final class TesseractOCRConfiguration
    extends OCRConfiguration<
      OCRAdapterInitParams.Empty,
      OCRAdapterOCRParams.Tesseract,
      TesseractAdapter
    > {
  private static final String DEFAULT_BIN_PATH = "tesseract";

  public TesseractOCRConfiguration(OCR.Configuration config) {
    super(config);
    adapterInitParams = new OCRAdapterInitParams.Empty();
    adapterOCRParams = new OCRAdapterOCRParams.Tesseract(
      config.tesseractBinPath() != null ? config.tesseractBinPath() : DEFAULT_BIN_PATH,
      config.tesseractModel(),
      config.tesseractPSM(),
      config.tesseractModelAlt(),
      config.tesseractPSMAlt()
    );
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new TesseractAdapter();
  }
}
