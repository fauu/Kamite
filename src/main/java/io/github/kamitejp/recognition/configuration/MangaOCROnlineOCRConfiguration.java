package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;

public final class MangaOCROnlineOCRConfiguration extends OCRConfiguration {
  private OCREngine.MangaOCROnline engine;

  public MangaOCROnlineOCRConfiguration(OCR.Configuration config) {
    super(config);
  }

  @Override
  public OCREngineParams getEngineParams() {
    return null;
  }

  public void setEngine(OCREngine.MangaOCROnline engine) {
    this.engine = engine;
  }
}

