package io.github.kamitejp.platform.mpv;

import java.util.function.Consumer;

import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.status.PlayerStatus;

public interface MPVController {
  void sendCommand(MPVCommand cmd);

  void destroy();

  static MPVController create(
    Platform platform, Consumer<PlayerStatus> statusUpdateCb, Consumer<Subtitle> subtitleCb
  ) {
    var controller = platform.getOS() == OS.WINDOWS
      ? new WindowsMPVController()
      : new UnixMPVController();
    controller.init(statusUpdateCb, subtitleCb);
    return controller;
  }
}
