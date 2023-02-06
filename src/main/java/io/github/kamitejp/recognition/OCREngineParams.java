package io.github.kamitejp.recognition;

public sealed interface OCREngineParams
  permits OCREngineParams.Tesseract {

  record Tesseract(String binPath) implements OCREngineParams {}
}
