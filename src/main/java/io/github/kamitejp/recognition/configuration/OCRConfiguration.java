package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCREngineParams;
import io.github.kamitejp.util.Result;

public abstract class OCRConfiguration {
  private String name;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  public abstract OCREngineParams getEngineParams();

  public abstract Result<Void, String> initEngine();
}

