package io.github.kamitejp.recognition.adapter;

import java.awt.image.BufferedImage;

import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.LocalOcrError;
import io.github.kamitejp.util.Result;

public interface LocalOcrAdapter<R extends OcrAdapterOcrParams> extends OcrAdapter<R> {
  Result<BoxRecognitionOutput, LocalOcrError> recognize(BufferedImage img, R params);
}
