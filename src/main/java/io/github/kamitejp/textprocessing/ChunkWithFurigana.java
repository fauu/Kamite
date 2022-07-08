package io.github.kamitejp.textprocessing;

import java.util.List;

public class ChunkWithFurigana {
  private List<MaybeRuby> maybeRubies;

  public ChunkWithFurigana(List<MaybeRuby> maybeRubies) {
    this.maybeRubies = maybeRubies;
  }

  public List<MaybeRuby> getMaybeRubies() {
    return this.maybeRubies;
  }
}
