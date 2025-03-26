package io.github.kamitejp.recognition;

public sealed interface OCRAdapterOCRParams
  permits OCRAdapterOCRParams.Tesseract,
          OCRAdapterOCRParams.Empty {
  record Tesseract(
    String binPath,
    String model,
    int psm,
    String modelAlt,
    Integer psmAlt
  ) implements OCRAdapterOCRParams {}

  record Empty() implements OCRAdapterOCRParams {}
}
