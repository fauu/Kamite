package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;

import io.github.kamitejp.util.Result;

public interface LocalOCRAdapter<R extends OCRAdapterOCRParams> extends OCRAdapter<R> {
  Result<BoxRecognitionOutput, LocalOCRError> recognize(BufferedImage img, R params);
}
