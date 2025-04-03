package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.MangaOcrController;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterPreInitializationException;

public final class MangaOcrOcrConfiguration
    extends OcrConfiguration<
        OcrAdapterInitParams.MangaOCR,
        OcrAdapterOcrParams.Empty,
        MangaOcrController> {
  public MangaOcrOcrConfiguration(Ocr.Configuration config) {
    super(
        config,
        new OcrAdapterInitParams.MangaOCR(config.pythonPath()),
        new OcrAdapterOcrParams.Empty());
  }

  @Override
  public void createAdapter(Platform platform)
      throws OcrAdapterPreInitializationException {
    adapter = new MangaOcrController(platform, adapterInitParams.pythonPath());
  }
}
