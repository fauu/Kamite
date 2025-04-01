package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OcrAdapterInitParams;
import io.github.kamitejp.recognition.OcrAdapterOcrParams;
import io.github.kamitejp.recognition.OcrSpaceAdapter;

public final class OcrSpaceOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.OCRSpace,
        OcrSpaceAdapter> {
  public OcrSpaceOcrConfiguration(Ocr.Configuration config) {
    super(config);
    adapterInitParams = new OcrAdapterInitParams.Empty();
    adapterOcrParams = new OcrAdapterOcrParams.OCRSpace(config.apiKey());
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new OcrSpaceAdapter();
  }
}

