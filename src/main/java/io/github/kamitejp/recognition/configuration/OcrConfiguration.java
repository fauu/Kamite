package io.github.kamitejp.recognition.configuration;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.config.Config.Ocr;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.adapter.OcrAdapter;
import io.github.kamitejp.recognition.adapter.OcrAdapterInitParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterOcrParams;
import io.github.kamitejp.recognition.adapter.OcrAdapterPreInitializationException;
import io.github.kamitejp.recognition.OcrError;
import io.github.kamitejp.recognition.adapter.RemoteOcrAdapter;
import io.github.kamitejp.util.Result;

public abstract class OcrConfiguration<
      P extends OcrAdapterInitParams,
      R extends OcrAdapterOcrParams,
      A extends OcrAdapter<R>> {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected P adapterInitParams;
  protected R adapterOcrParams;
  protected A adapter;

  private final String name;
  private OcrConfigurationStatus status;

  protected OcrConfiguration(
      Ocr.Configuration config,
      P adapterInitParams,
      R adapterOcrParams) {
    name = config.name();
    this.adapterInitParams = adapterInitParams;
    this.adapterOcrParams = adapterOcrParams;
  }

  public abstract void createAdapter(Platform platform) throws OcrAdapterPreInitializationException;

  public Result<BoxRecognitionOutput, ? extends OcrError> recognize(BufferedImage img) {
    LOG.debug("Current recognition operation is using configuration '{}'", getClass().getName());
    if (adapter instanceof RemoteOcrAdapter) {
      @SuppressWarnings("unchecked")
      var remoteAdapter = (RemoteOcrAdapter<R>) adapter;
      return remoteAdapter.recognizeWithRetry(img, adapterOcrParams);
    }
    return adapter.recognize(img, adapterOcrParams);
  }

  public String getName() {
    return name;
  }

  public OcrConfigurationStatus getStatus() {
    return status;
  }

  public void setStatus(OcrConfigurationStatus status) {
    this.status = status;
  }

  public P getAdapterInitParams() {
    return adapterInitParams;
  };

  public R getAdapterOcrParams() {
    return adapterOcrParams;
  };

  public A getAdapter() {
    return adapter;
  }

  public void setAdapter(A adapter) {
    this.adapter = adapter;
  }
}
