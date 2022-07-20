package io.github.kamitejp.platform;

public enum OS {
  WINDOWS(OSFamily.WINDOWS),
  LINUX(OSFamily.UNIX),
  MACOS(OSFamily.UNIX);

  private final OSFamily family;

  OS(OSFamily family) {
    this.family = family;
  }

  public OSFamily getFamily() {
    return family;
  }
}
