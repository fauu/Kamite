package io.github.kamitejp.recognition;

public sealed interface OCRAdapterInitParams
  permits OCRAdapterInitParams.MangaOCR,
          OCRAdapterInitParams.Empty {
  record MangaOCR(String pythonPath) implements OCRAdapterInitParams {}
  record Empty() implements OCRAdapterInitParams {}
}
