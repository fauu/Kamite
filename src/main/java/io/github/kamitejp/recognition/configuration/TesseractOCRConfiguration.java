package io.github.kamitejp.recognition.configuration;

import java.awt.image.BufferedImage;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;
import io.github.kamitejp.recognition.TesseractModelType;
import io.github.kamitejp.util.Result;

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

  public boolean hasAltModel() {
    return modelAlt != null;
  }

  public Result<String, String> ocr(BufferedImage img, TesseractModelType modelType) {
    var engine = getEngine();
    var adapter = engine.getAdapter();
    // XXX: This is a mess
    return switch (modelType) {
      case DEFAULT -> adapter.ocr(img, engine.binPath, model, psm);
      case ALT     -> adapter.ocr(img, engine.binPath, modelAlt, psmAlt != null ? psmAlt : psm);
    };
  }
}
