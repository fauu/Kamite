package io.github.kamitejp.universalfeature;

public abstract class UnavailableUniversalFeature {
  private String id;
  private UniversalFeatureUnavailableReason reason;

  public UnavailableUniversalFeature(String id, UniversalFeatureUnavailableReason reason) {
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
