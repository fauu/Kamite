package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.MangaOCRHFAdapter;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;

public final class MangaOCROnlineOCRConfiguration
    extends OCRConfiguration<
      OCRAdapterInitParams.Empty,
      OCRAdapterOCRParams.Empty,
      MangaOCRHFAdapter
    > {
  public MangaOCROnlineOCRConfiguration(OCR.Configuration config) {
    super(config);
    adapterInitParams = new OCRAdapterInitParams.Empty();
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new MangaOCRHFAdapter();
  }
}

