package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;

import io.github.kamitejp.util.Result;

public interface RemoteOCRAdapter<R extends OCRAdapterOCRParams> extends OCRAdapter<R> {
  Result<BoxRecognitionOutput, RemoteOCRError> recognize(BufferedImage img, R params);
}