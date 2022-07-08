package io.github.kamitejp.platform;

import java.util.Locale;

public enum CPUArchitecture {
  AMD64;

  @Override
  public String toString() {
    return super.toString().toLowerCase(Locale.ENGLISH);
  }
}
