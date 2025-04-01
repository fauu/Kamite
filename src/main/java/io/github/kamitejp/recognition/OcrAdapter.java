package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;

import io.github.kamitejp.util.Result;

public interface OcrAdapter<R extends OcrAdapterOcrParams> {
  Result<BoxRecognitionOutput, ? extends OcrError> recognize(BufferedImage img, R params);

  void destroy();
}
