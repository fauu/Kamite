package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;
import io.github.kamitejp.recognition.OCRSpaceAdapter;

public final class OCRSpaceOCRConfiguration
    extends OCRConfiguration<
        OCRAdapterInitParams.Empty,
        OCRAdapterOCRParams.OCRSpace,
        OCRSpaceAdapter> {
  public OCRSpaceOCRConfiguration(OCR.Configuration config) {
    super(config);
    adapterInitParams = new OCRAdapterInitParams.Empty();
    adapterOCRParams = new OCRAdapterOCRParams.OCRSpace(config.apiKey());
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new OCRSpaceAdapter();
  }
}

