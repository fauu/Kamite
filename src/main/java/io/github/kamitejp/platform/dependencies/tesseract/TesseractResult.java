package io.github.kamitejp.platform.dependencies.tesseract;

public sealed interface TesseractResult
  permits TesseractResult.ExecutionFailed,
          TesseractResult.Error,
          TesseractResult.HOCR {
  record ExecutionFailed() implements TesseractResult {}
  record Error(String error) implements TesseractResult {}
  record HOCR(String hocr) implements TesseractResult {}
}
