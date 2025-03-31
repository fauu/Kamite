package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.GLensHFAdapter;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;

public final class GLensOCRConfiguration
    extends OCRConfiguration<
        OCRAdapterInitParams.Empty,
        OCRAdapterOCRParams.Empty,
        GLensHFAdapter> {
  public GLensOCRConfiguration(OCR.Configuration config) {
    super(config);
    adapterInitParams = new OCRAdapterInitParams.Empty();
    adapterOCRParams = new OCRAdapterOCRParams.Empty();
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new GLensHFAdapter();
  }
}

