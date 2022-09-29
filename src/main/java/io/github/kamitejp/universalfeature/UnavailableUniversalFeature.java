package io.github.kamitejp.universalfeature;

@SuppressWarnings("AbstractClassWithoutAbstractMethods")
public abstract class UnavailableUniversalFeature {
  private final String id;
  private final UniversalFeatureUnavailableReason reason;

  UnavailableUniversalFeature(String id, UniversalFeatureUnavailableReason reason) {
    this.id = id;
    this.reason = reason;
  }

  public String getID() {
    return id;
  }

  public UniversalFeatureUnavailableReason getReason() {
    return reason;
  }
}
