package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;

import io.github.kamitejp.util.Result;

public interface OCRAdapter<R extends OCRAdapterOCRParams> {
  Result<BoxRecognitionOutput, ? extends OCRError> recognize(BufferedImage img, R params);
}
