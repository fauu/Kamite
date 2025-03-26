package io.github.kamitejp.recognition;

public sealed interface LocalOCRError extends OCRError
    permits LocalOCRError.Other {
  record Other(String error) implements LocalOCRError {}
}