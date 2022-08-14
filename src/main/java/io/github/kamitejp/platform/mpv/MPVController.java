package io.github.kamitejp.platform.mpv;

import java.util.function.Consumer;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.status.PlayerStatus;

public interface MPVController {
  void sendCommand(MPVCommand cmd);

  void destroy();

  static MPVController create(
    Platform platform, Config config, Consumer<PlayerStatus> statusUpdateCb
  ) {
    var controller = platform.getOS() == OS.WINDOWS
      ? new WindowsMPVController()
      : new UnixMPVController();
    controller.init(platform, config.server().port(), statusUpdateCb);
    return controller;
  }
}
