package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;

public final class TesseractOCRConfiguration extends OCRConfiguration<OCREngine.Tesseract, OCREngineParams.Tesseract> {
  private static final String DEFAULT_BIN_PATH = "tesseract";

  private final OCREngineParams.Tesseract engineInitParams;
  private final String model;
  private final int psm;
  private final String modelAlt;
  private final Integer psmAlt;

  public TesseractOCRConfiguration(OCR.Configuration config) {
    super(config);
    var binPath = config.path() != null ? config.path() : DEFAULT_BIN_PATH;
    engineInitParams = new OCREngineParams.Tesseract(binPath);
    model = config.tesseractModel();
    psm = config.tesseractPSM();
    modelAlt = config.tesseractModelAlt();
    psmAlt = config.tesseractPSMAlt();
  }

  @Override
  public OCREngineParams.Tesseract getEngineParams() {
    return engineInitParams;
  }
}
