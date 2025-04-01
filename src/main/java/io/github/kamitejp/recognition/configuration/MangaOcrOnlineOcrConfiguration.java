package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.adapter.MangaOcrHfAdapter;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;

public final class MangaOcrOnlineOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.Empty,
        OcrAdapterOcrParams.Empty,
        MangaOcrHfAdapter> {
  public MangaOcrOnlineOcrConfiguration(Ocr.Configuration config) {
    super(config);
    adapterInitParams = new OcrAdapterInitParams.Empty();
    adapterOcrParams = new OcrAdapterOcrParams.Empty();
  }

  @Override
  public void createAdapter(Platform platform) {
    adapter = new MangaOcrHfAdapter();
  }
}

