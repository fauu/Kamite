package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;
import io.github.kamitejp.recognition.adapter.OcrSpaceAdapter;

public final class OcrSpaceOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.OCRSpace,
        OcrSpaceAdapter> {
  public OcrSpaceOcrConfiguration(Ocr.Configuration config) {
    super(
        config,
        new OcrAdapterInitParams.Empty(),
        new OcrAdapterOcrParams.OCRSpace(config.apiKey()));
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new OcrSpaceAdapter();
  }
}

