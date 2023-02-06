package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;

public final class MangaOCROnlineOCRConfiguration extends OCRConfiguration {
  private final OCREngine.MangaOCROnline engine;

  public MangaOCROnlineOCRConfiguration(OCR.Configuration config) {
    super(config);
    engine = OCREngine.MangaOCROnline.uninitialized();
  }

  public OCREngine.MangaOCROnline getEngine() {
    return engine;
  }
}

