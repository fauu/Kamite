package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngine;

public abstract class OCRConfiguration {
  private String name;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  protected abstract OCREngine getEngine();
}

