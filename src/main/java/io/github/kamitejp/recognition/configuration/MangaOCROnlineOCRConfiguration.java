package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.MangaOCRHFAdapter;
import io.github.kamitejp.recognition.OCRAdapterInitParams;

public final class MangaOCROnlineOCRConfiguration extends OCRConfiguration<OCRAdapterInitParams.Empty> {
  private MangaOCRHFAdapter adapter = null;

  public MangaOCROnlineOCRConfiguration(OCR.Configuration config) {
    super(config);
    adapterInitParams = new OCRAdapterInitParams.Empty();
  }

  public void initAdapter() {
    adapter = new MangaOCRHFAdapter();
  }

  public MangaOCRHFAdapter getAdapter() {
    return adapter;
  }
}

