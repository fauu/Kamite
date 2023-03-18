package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.OCREngineParams;

public abstract class OCRConfiguration<E extends OCREngine, P extends OCREngineParams> {
  private String name;
  protected P engineInitParams;
  private E engine;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  public void setEngine(E engine) {
    this.engine = engine;
  }

  public P getEngineParams() {
    return engineInitParams;
  };

  public E getEngine() {
    return engine;
  }
}

