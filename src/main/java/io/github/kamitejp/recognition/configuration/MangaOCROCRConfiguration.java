package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;

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
  public void createAdapter(Platform platform/* XXX , Consumer<MangaOCREvent> eventCb*/) {
    // XXX
    // adapter = new MangaOCRController(platform, pythonPath, eventCb);
    adapter = new MangaOCRController(platform, pythonPath);
  }
}
