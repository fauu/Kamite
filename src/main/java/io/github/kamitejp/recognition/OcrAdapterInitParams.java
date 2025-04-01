package io.github.kamitejp.recognition;

public sealed interface OcrAdapterInitParams
  permits OcrAdapterInitParams.MangaOCR,
          OcrAdapterInitParams.Empty {
  record MangaOCR(String pythonPath) implements OcrAdapterInitParams {}
  record Empty() implements OcrAdapterInitParams {}
}
