package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.EasyOcrHfAdapter;
import io.github.kamitejp.recognition.OcrAdapterInitParams;
import io.github.kamitejp.recognition.OcrAdapterOcrParams;

public final class EasyOcrOnlineOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.Empty,
        EasyOcrHfAdapter> {
  public EasyOcrOnlineOcrConfiguration(Ocr.Configuration config) {
    super(config);
    adapterInitParams = new OcrAdapterInitParams.Empty();
    adapterOcrParams = new OcrAdapterOcrParams.Empty();
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new EasyOcrHfAdapter();
  }
}

