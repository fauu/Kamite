package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.status.PlayerStatus;

public abstract class AbstractMPVController implements MPVController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected static final String IPC_MEDIUM_FILENAME = "kamite-mpvsocket";
  protected static final int CONNECTION_RETRY_INTERVAL_MS = 2000;

  private static final String SCRIPT_FILENAME = "kamite_mpv.lua";

  protected Platform platform;
  protected Consumer<PlayerStatus> statusUpdateCb;
  protected State state;

  private int kamitePort;

  public AbstractMPVController() {
    state = State.NOT_CONNECTED;
  }

  public void init(Platform platform, int kamitePort, Consumer<PlayerStatus> statusUpdateCb) {
    this.platform = platform;
    this.kamitePort = kamitePort;
    this.statusUpdateCb = statusUpdateCb;
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

  protected abstract void sendBytes(byte[] bytes) throws IOException;

  protected boolean handleMessages(String messagesJSON) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Received mpv messages: {}", ipcJSONToPrintable(messagesJSON));
    }

    boolean gotQuitMessage = false;
    PlayerStatus statusUpdate = null;

    for (var msg : MPVMessage.manyFromJSON(messagesJSON)) {
      switch (msg) {
        case END_FILE             -> gotQuitMessage = true;
        case PAUSED               -> statusUpdate = PlayerStatus.PAUSED;
        case UNPAUSED             -> statusUpdate = PlayerStatus.PLAYING;
        case KAMITE_SCRIPT_LOADED -> sendCommand(new MPVCommand.InitKamiteScript(kamitePort));
        case UNRECOGNIZED         -> LOG.trace("Did not handle an unrecognized mpv message");
      }
    }

    if (statusUpdate != null) {
      statusUpdateCb.accept(statusUpdate);
    }

    return gotQuitMessage;
  }

  private String ipcJSONToPrintable(String json) {
    return json.replace("\n", "\\n");
  }

  protected abstract class Worker implements Runnable {
    protected final Function<String, Boolean> messagesCb;

    public Worker(Function<String, Boolean> messagesCb) {
      this.messagesCb = messagesCb;
    }

    public void run() {
      LOG.debug("Waiting for mpv connection");
      connect();
      state = State.CONNECTED;

      // The external world will be notified of the established connection when we handle the
      // incoming pause status update
      sendCommand(new MPVCommand.ObservePause());

      sendCommand(new MPVCommand.LoadKamiteScript(
        platform.getGenericLibDirPath().resolve(SCRIPT_FILENAME).toString())
      );

      // This will block until requested or until the connection closes, depending on implementation
      runReader();

      statusUpdateCb.accept(PlayerStatus.DISCONNECTED);
      state = State.NOT_CONNECTED;
      LOG.info("mpv disconnected");

      // Give a moment for the connection to properly close on the other end. Without this on UNIX
      // we would immediately connect to the expired socket and cause an exception by trying to
      // write to it
      try {
        Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting for mpv connection to close. Aborting");
        return;
      }

      // Wait for a new connection
      run();
    }

    protected abstract boolean connect();

    protected abstract void runReader();
  }

  enum State {
    CONNECTED,
    NOT_CONNECTED
  }
}
