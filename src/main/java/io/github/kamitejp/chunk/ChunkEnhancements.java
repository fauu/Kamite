package io.github.kamitejp.chunk;

import java.util.List;

public record ChunkEnhancements(List<Integer> interVariantUniqueCharacterIndices) {
  public static ChunkEnhancements ofInterVariantUniqueCharacterIndices(
    List<Integer> interVariantUniqueCharacterIndices
  ) {
    return new ChunkEnhancements(interVariantUniqueCharacterIndices);
  }

  public static ChunkEnhancements empty() {
    return new ChunkEnhancements(null);
  }
}
