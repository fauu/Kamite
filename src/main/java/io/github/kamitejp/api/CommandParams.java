package io.github.kamitejp.api;

import io.github.kamitejp.chunk.ChunkTranslationDestination;
import io.github.kamitejp.recognition.PointSelectionMode;

public interface CommandParams {
  interface Ocr {
    record AutoBlock(String configurationName, PointSelectionMode mode) {}
    record AutoColumn(String configurationName, PointSelectionMode mode) {}
    record Region(String configurationName, int x, int y, int width, int height, boolean autoNarrow) {}
    record Image(String configurationName, String bytesB64, int width, int height) {}
  }

  interface OcrSetup {
    record SetActiveOcrConfiguration(String name) {}
  }

  interface Chunk {
    record Show(String chunk, Double playbackTimeS) {}
    record ShowTranslation(
        String translation,
        ChunkTranslationDestination destination,
        Double playbackTimeS) {}
  }

  interface Misc {
    record Custom(String[] command) {}
    record Lookup(String targetSymbol, String customText) {}
  }
}
