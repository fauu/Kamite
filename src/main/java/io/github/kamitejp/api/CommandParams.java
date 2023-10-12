package io.github.kamitejp.api;

import io.github.kamitejp.chunk.ChunkTranslationDestination;
import io.github.kamitejp.recognition.PointSelectionMode;

public interface CommandParams {
  interface OCR {
    record AutoBlock(PointSelectionMode mode) {}
    record AutoColumn(PointSelectionMode mode) {}
    record Region(int x, int y, int width, int height, boolean autoNarrow) {}
    record Image(String bytesB64, int width, int height) {}
  }

  interface Chunk {
    record Show(String chunk, Double playbackTimeS) {}
    record ShowTranslation(
      String translation,
      ChunkTranslationDestination destination,
      Double playbackTimeS
    ) {}
  }

  interface Misc {
    record Custom(String[] command) {}
    record Lookup(String targetSymbol, String customText) {}
  }
}
