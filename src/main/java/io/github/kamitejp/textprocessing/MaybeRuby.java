package io.github.kamitejp.textprocessing;

public record MaybeRuby(String base, String text) {
  public static MaybeRuby ruby(String base, String text) {
    return new MaybeRuby(base, text);
  }

  public static MaybeRuby notRuby(String base) {
    return new MaybeRuby(base, null);
  }
}
