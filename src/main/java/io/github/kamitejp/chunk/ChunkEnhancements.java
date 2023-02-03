package io.github.kamitejp.chunk;

import java.util.List;

import io.github.kamitejp.textprocessing.MaybeRuby;

public record ChunkEnhancements(
  List<Integer> interVariantUniqueCharacterIndices,
  List<MaybeRuby> furiganaMaybeRubies
) {
  public static ChunkEnhancements ofInterVariantUniqueCharacterIndices(
    List<Integer> interVariantUniqueCharacterIndices
  ) {
    return new ChunkEnhancements(interVariantUniqueCharacterIndices, null);
  }

  public static ChunkEnhancements ofFuriganaMaybeRubies(List<MaybeRuby> furiganaMaybeRubies) {
    return new ChunkEnhancements(null, furiganaMaybeRubies);
  }

  public static ChunkEnhancements empty() {
    return new ChunkEnhancements(null, null);
  }
}
