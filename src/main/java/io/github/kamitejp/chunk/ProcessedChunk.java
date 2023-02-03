package io.github.kamitejp.chunk;

import java.util.List;

public record ProcessedChunk(
  String content,
  String originalContent,
  List<String> labels,
  int score,
  ChunkEnhancements enhancements
) {
  public static ProcessedChunk fromChunk(Chunk chunk, ChunkEnhancements enhancements) {
    return new ProcessedChunk(
        chunk.getContent(),
        chunk.getOriginalContent(),
        chunk.getLabels(),
        chunk.getScore(),
        enhancements
    );
  }
}
