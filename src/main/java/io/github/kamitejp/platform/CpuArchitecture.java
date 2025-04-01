package io.github.kamitejp.platform;

import java.util.Locale;

public enum CpuArchitecture {
  AMD64;

  @Override
  public String toString() {
    return super.toString().toLowerCase(Locale.ENGLISH);
  }
}
