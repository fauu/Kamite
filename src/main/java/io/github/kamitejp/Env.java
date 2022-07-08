package io.github.kamitejp;

import io.github.kamitejp.platform.GenericPlatform;

public final class Env {
  private Env() {}

  public static boolean isDevMode() {
    var devEnv = GenericPlatform.getEnvVarAsNonNullableString("DEV");
    return "1".equalsIgnoreCase(devEnv) || "true".equalsIgnoreCase(devEnv);
  }
}
