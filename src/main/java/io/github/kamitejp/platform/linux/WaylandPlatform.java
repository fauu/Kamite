package io.github.kamitejp.platform.linux;

import io.github.kamitejp.platform.PlatformCreationException;

public abstract class WaylandPlatform extends LinuxPlatform {
  public WaylandPlatform() throws PlatformCreationException {
    if (getEnvVarAsNonNullableString("WAYLAND_DISPLAY").isEmpty()) {
      throw new PlatformCreationException("WAYLAND_DISPLAY is not set");
    }
  }
}
