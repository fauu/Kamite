package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;

public final class TesseractOCRConfiguration extends OCRConfiguration {
  private static final String DEFAULT_BIN_PATH = "tesseract";

  private final OCREngine.Tesseract engine;
  private final String model;
  private final int psm;
  private final String modelAlt;
  private final Integer psmAlt;

  public TesseractOCRConfiguration(OCR.Configuration config) {
    super(config);
    var binPath = config.path() != null ? config.path() : DEFAULT_BIN_PATH;
    engine = new OCREngine.Tesseract(binPath);
    model = config.tesseractModel();
    psm = config.tesseractPSM();
    modelAlt = config.tesseractModelAlt();
    psmAlt = config.tesseractPSMAlt();
  }

  public OCREngine.Tesseract getEngine() {
    return engine;
  }
}

