package io.github.kamitejp.textprocessing;

// QUAL: Should rename either the record or the `notation` field
record Notation(String base, NotationBaseType baseType, String notation) {
  public Notation withNotation(String notation) {
    return new Notation(base, baseType, notation);
  }
}

