package io.github.kamitejp.platform.mpv;

import java.util.function.Consumer;

import io.github.kamitejp.platform.Os;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.status.PlayerStatus;

public interface MpvController {
  void sendCommand(MpvCommand cmd);

  void destroy();

  static MpvController create(
    Platform platform, Consumer<PlayerStatus> statusUpdateCb, Consumer<Subtitle> subtitleCb
  ) {
    var controller = platform.getOs() == Os.WINDOWS
      ? new WindowsMPVController()
      : new UnixMpvController();
    controller.init(statusUpdateCb, subtitleCb);
    return controller;
  }
}
