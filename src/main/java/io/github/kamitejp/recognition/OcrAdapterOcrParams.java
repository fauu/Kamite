package io.github.kamitejp.recognition;

public sealed interface OcrAdapterOcrParams
  permits OcrAdapterOcrParams.Tesseract,
          OcrAdapterOcrParams.OCRSpace,
          OcrAdapterOcrParams.Empty {
  record Tesseract(
      String binPath,
      String model,
      int psm,
      String modelAlt,
      Integer psmAlt) implements OcrAdapterOcrParams {}

  record OCRSpace(String apiKey) implements OcrAdapterOcrParams {}

  record Empty() implements OcrAdapterOcrParams {}
}
