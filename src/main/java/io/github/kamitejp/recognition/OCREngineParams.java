package io.github.kamitejp.recognition;

public sealed interface OCREngineParams
  permits OCREngineParams.Tesseract,
          OCREngineParams.Empty {

  record Tesseract(String binPath) implements OCREngineParams {}
  record Empty() implements OCREngineParams {}
}
