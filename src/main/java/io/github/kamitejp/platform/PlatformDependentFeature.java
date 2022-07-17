package io.github.kamitejp.platform;

public enum PlatformDependentFeature {
  GLOBAL_OCR("Global OCR", "text recognition of arbitrary screen areas");

  public final String displayName;
  public final String description;

  PlatformDependentFeature(String displayName, String msg) {
    this.displayName = displayName;
    this.description = msg;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }
}
