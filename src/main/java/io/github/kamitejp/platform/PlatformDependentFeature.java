package io.github.kamitejp.platform;

public enum PlatformDependentFeature {
  GLOBAL_OCR("Global OCR", "text recognition of arbitrary screen areas"),
  GLOBAL_KEYBINDINGS("Registering global keybindings", "automatically setup keybindings that work outside the client’s browser tab");

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
