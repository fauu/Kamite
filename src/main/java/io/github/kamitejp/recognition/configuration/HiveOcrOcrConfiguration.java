package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.adapter.HiveOcrHfAdapter;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;

public final class HiveOcrOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.Empty,
        HiveOcrHfAdapter> {
  public HiveOcrOcrConfiguration(Ocr.Configuration config) {
    super(config, new OcrAdapterInitParams.Empty(), new OcrAdapterOcrParams.Empty());
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new HiveOcrHfAdapter();
  }
}

