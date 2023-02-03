package io.github.kamitejp.chunk;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ChunkEnhancement {
  FURIGANA;

  @JsonValue
  public String jsonValue() {
    return name().toLowerCase().replace('_', '-');
  }
}
