package io.github.kamitejp.recognition;

public sealed interface TesseractResult
  permits TesseractResult.ExecutionFailed,
          TesseractResult.TimedOut,
          TesseractResult.Error,
          TesseractResult.HOCR {
  record ExecutionFailed() implements TesseractResult {}
  record TimedOut() implements TesseractResult {}
  record Error(String error) implements TesseractResult {}
  record HOCR(String hocr) implements TesseractResult {}
}
