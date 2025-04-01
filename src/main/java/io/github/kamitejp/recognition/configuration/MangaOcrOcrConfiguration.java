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
  private final String pythonPath;

  public MangaOcrOcrConfiguration(Ocr.Configuration config) {
    super(config);
    pythonPath = config.pythonPath();
    adapterInitParams = new OcrAdapterInitParams.MangaOCR(pythonPath);
  }

  @Override
  public void createAdapter(Platform platform)
      throws OcrAdapterPreInitializationException {
    adapter = new MangaOcrController(platform, pythonPath);
  }
}
