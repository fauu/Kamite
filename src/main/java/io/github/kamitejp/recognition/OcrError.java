package io.github.kamitejp.recognition;

public sealed interface OcrError
  permits LocalOcrError, RemoteOcrError {}
