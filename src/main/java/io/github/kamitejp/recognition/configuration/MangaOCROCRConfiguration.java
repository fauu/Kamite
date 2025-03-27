package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;
import io.github.kamitejp.recognition.OCRAdapterPreinitializationException;

public final class MangaOCROCRConfiguration
    extends OCRConfiguration<
      OCRAdapterInitParams.MangaOCR,
      OCRAdapterOCRParams.Empty,
      MangaOCRController
    > {
  private final String pythonPath;

  public MangaOCROCRConfiguration(OCR.Configuration config) {
    super(config);
    pythonPath = config.pythonPath();
    adapterInitParams = new OCRAdapterInitParams.MangaOCR(pythonPath);
  }

  @Override
  public void createAdapter(Platform platform) throws OCRAdapterPreinitializationException {
    adapter = new MangaOCRController(platform, pythonPath);
  }
}
