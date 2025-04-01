package io.github.kamitejp.platform;

public enum Os {
  WINDOWS(OsFamily.WINDOWS),
  LINUX(OsFamily.UNIX),
  MACOS(OsFamily.UNIX);

  private final OsFamily family;

  Os(OsFamily family) {
    this.family = family;
  }

  public OsFamily getFamily() {
    return family;
  }
}
