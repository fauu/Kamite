package io.github.kamitejp.chunk;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ChunkEnhancement {
  FURIGANA;

  @JsonValue
  public String jsonValue() {
    return name().toLowerCase(Locale.ENGLISH).replace('_', '-');
  }
}
