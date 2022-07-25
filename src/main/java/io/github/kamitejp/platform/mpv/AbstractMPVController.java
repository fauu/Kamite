package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.status.PlayerStatus;

public abstract class AbstractMPVController implements MPVController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected static final String PIPE_FILENAME = "kamite-mpvsocket";

  protected static final int CONNECTION_RETRY_INTERVAL_MS = 2000;
  protected static final int GET_PAUSE_REQUEST_ID = 100;

  protected Consumer<PlayerStatus> statusUpdateCb;
  protected State state;

  public AbstractMPVController(Consumer<PlayerStatus> statusUpdateCb) {
    this.statusUpdateCb = statusUpdateCb;
    state = State.NOT_CONNECTED;
  }

  public State getState() {
    return state;
  }

  public void sendCommand(MPVCommand cmd) {
    if (state != State.CONNECTED) {
      LOG.debug("Tried to send mpv command while not connected");
      return;
    }

    var cmdJSON = cmd.toJSON();
    try {
      sendBytes(cmdJSON.getBytes(StandardCharsets.UTF_8));
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sent mpv IPC command: {}", ipcJSONToPrintable(cmdJSON));
      }
    } catch (IOException e) {
      LOG.error("Failed to write to mpv UNIX socket: ", e);
    }
  }

  protected void sendBytes(byte[] bytes) throws IOException {
    throw new IllegalStateException("sendBytes() not implemented");
  }

  // Returns true if the message is a quit message
  protected boolean handleMessage(String msgJSON) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Received mpv message: {}", ipcJSONToPrintable(msgJSON));
    }

    PlayerStatus update = null;

    switch (MPVMessage.fromJSON(msgJSON)) {
      case END_FILE:
        return true;
      case PAUSED:
        update = PlayerStatus.PAUSED;
        break;
      case UNPAUSED:
        update = PlayerStatus.PLAYING;
        break;
      default:
        LOG.debug("Did not know how to handle the mpv message");
    }

    if (update != null) {
      statusUpdateCb.accept(update);
    }

    return false;
  }

  private String ipcJSONToPrintable(String json) {
    return json.replace("\n", "\\n");
  }


  enum State {
    CONNECTED,
    NOT_CONNECTED
  }
}
