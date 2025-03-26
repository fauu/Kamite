package io.github.kamitejp.recognition;

public sealed interface OCRError
  permits LocalOCRError, RemoteOCRError {}