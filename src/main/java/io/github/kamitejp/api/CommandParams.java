package io.github.kamitejp.api;

public interface CommandParams {
  interface OCR {
    record Region(int x, int y, int width, int height, boolean autoNarrow) {};
    record Image(String bytesB64, int width, int height) {};
  }

  interface Chunk {
    record Show(String chunk, Double playbackTimeS) {};
    record ShowTranslation(String translation, Double playbackTimeS) {};
  }

  interface Other {
    record Custom(String[] command) {};
  }
}
