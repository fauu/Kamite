package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;

public final class MangaOCROnlineOCRConfiguration extends OCRConfiguration<OCREngine.MangaOCROnline, OCREngineParams.Empty> {
  public MangaOCROnlineOCRConfiguration(OCR.Configuration config) {
    super(config);
    engineInitParams = new OCREngineParams.Empty();
  }
}

