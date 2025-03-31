package io.github.kamitejp.recognition;

public sealed interface OCRAdapterOCRParams
  permits OCRAdapterOCRParams.Tesseract,
          OCRAdapterOCRParams.OCRSpace,
          OCRAdapterOCRParams.Empty {
  record Tesseract(
      String binPath,
      String model,
      int psm,
      String modelAlt,
      Integer psmAlt) implements OCRAdapterOCRParams {}

  record OCRSpace(String apiKey) implements OCRAdapterOCRParams {}

  record Empty() implements OCRAdapterOCRParams {}
}
