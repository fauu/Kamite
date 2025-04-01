package io.github.kamitejp.recognition;

public sealed interface LocalOcrError extends OcrError
    permits LocalOcrError.Other {
  record Other(String error) implements LocalOcrError {}
}
