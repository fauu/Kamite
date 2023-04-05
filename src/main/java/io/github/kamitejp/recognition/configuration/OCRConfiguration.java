package io.github.kamitejp.recognition.configuration;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.OCRAdapter;
import io.github.kamitejp.recognition.OCRAdapterInitParams;

public abstract class OCRConfiguration<P extends OCRAdapterInitParams, A extends OCRAdapter> {
  private String name;
  protected P adapterInitParams;
  protected A adapter;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  public abstract void createAdapter(Platform platform);

  public P getAdapterInitParams() {
    return adapterInitParams;
  };

  public A getAdapter() {
    return adapter;
  }

  public void setAdapter(A adapter) {
    this.adapter = adapter;
  }
}

