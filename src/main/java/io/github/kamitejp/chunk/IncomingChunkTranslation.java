package io.github.kamitejp.chunk;

public record IncomingChunkTranslation(
  String translation,
  ChunkTranslationDestination destination,
  Double playbackTimeS
) {}
