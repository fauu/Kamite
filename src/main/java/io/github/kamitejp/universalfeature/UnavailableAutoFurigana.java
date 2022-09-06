package io.github.kamitejp.universalfeature;

public final class UnavailableAutoFurigana extends UnavailableUniversalFeature {
  public UnavailableAutoFurigana(Reason reason) {
    super("auto-furigana", reason);
  }

  public enum Reason implements UniversalFeatureUnavailableReason {
    KUROMOJI_UNAVAILABLE
  }
}
