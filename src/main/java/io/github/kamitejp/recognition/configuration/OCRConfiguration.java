package io.github.kamitejp.recognition.configuration;

import java.awt.image.BufferedImage;

import io.github.kamitejp.config.Config.OCR;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.OCRAdapter;
import io.github.kamitejp.recognition.OCRAdapterInitParams;
import io.github.kamitejp.recognition.OCRAdapterOCRParams;
import io.github.kamitejp.recognition.OCRError;
import io.github.kamitejp.recognition.RemoteOCRAdapter;
import io.github.kamitejp.util.Result;

public abstract class OCRConfiguration<
    P extends OCRAdapterInitParams,
    R extends OCRAdapterOCRParams,
    A extends OCRAdapter<R>
  > {
  private String name;
  protected P adapterInitParams;
  protected R adapterOCRParams;
  protected A adapter;

  protected OCRConfiguration(OCR.Configuration config) {
    name = config.name();
  }

  public abstract void createAdapter(Platform platform);

  public Result<BoxRecognitionOutput, ? extends OCRError> recognize(BufferedImage img) {
    if (adapter instanceof RemoteOCRAdapter) {
      @SuppressWarnings("unchecked")
      var remoteAdapter = (RemoteOCRAdapter<R>) adapter;
      return remoteAdapter.recognizeWithRetry(img, adapterOCRParams);
    }
    return  adapter.recognize(img, adapterOCRParams);
  }

  public P getAdapterInitParams() {
    return adapterInitParams;
  };

  public R getAdapterOCRParams() {
    return adapterOCRParams;
  };

  public A getAdapter() {
    return adapter;
  }

  public void setAdapter(A adapter) {
    this.adapter = adapter;
  }
}