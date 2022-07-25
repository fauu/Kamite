package io.github.kamitejp.platform.mpv;

public interface MPVController {
  void sendCommand(MPVCommand cmd);

  State getState();

  void destroy();

  enum State {
    CONNECTED,
    NOT_CONNECTED
  }
}
