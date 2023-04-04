package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.recognition.OCRAdapterInitParams;

public abstract class OCRConfiguration<P extends OCRAdapterInitParams> {
  private String name;
  protected P adapterInitParams;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  public P getAdapterInitParams() {
    return adapterInitParams;
  };
}

