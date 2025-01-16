package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.status.PlayerStatus;

public abstract class BaseMPVController implements MPVController {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern ESCAPED_NEWLINE_RE = Pattern.compile("\\\\n");
  private static final Pattern ESCAPED_QUOTEMARK_RE = Pattern.compile("\\\\\"");

  static final String IPC_MEDIUM_FILENAME = "kamite-mpvsocket";
  static final int CONNECTION_RETRY_INTERVAL_MS = 2000;

  Consumer<PlayerStatus> statusUpdateCb;
  Consumer<Subtitle> subtitleCb;
  protected State state;

  private String[] pendingSubtitleTexts;

  BaseMPVController() {
    state = State.NOT_CONNECTED;
    pendingSubtitleTexts = new String[Subtitle.Kind.values().length];
  }

  public void init(Consumer<PlayerStatus> statusUpdateCb, Consumer<Subtitle> subtitleCb) {
    this.statusUpdateCb = statusUpdateCb;
    this.subtitleCb = subtitleCb;
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
      LOG.debug("Sent mpv IPC command: {}", () -> ipcJSONToPrintable(cmdJSON));
    } catch (IOException e) {
      LOG.error("Failed to write to mpv UNIX socket: ", e);
    }
  }

  protected abstract void sendBytes(byte[] bytes) throws IOException;

  boolean handleMessages(String messagesJSON) {
    LOG.debug("Received mpv messages: {}", () -> ipcJSONToPrintable(messagesJSON));

    boolean gotQuitMessage = false;
    PlayerStatus statusUpdate = null;

    for (var message : MPVMessage.parseMulti(messagesJSON)) {
      switch (message) { // NOPMD - misidentifies as non-exhaustive
        case MPVMessage.PropertyChange msg -> {
          switch (msg.name()) {
            case "pause" ->
              statusUpdate ="true".equalsIgnoreCase(msg.value())
                ? PlayerStatus.PAUSED
                : PlayerStatus.PLAYING;
            case "sub-text" ->
              handleSubtitleText(Subtitle.Kind.PRIMARY, msg.value());
            case "secondary-sub-text" ->
              handleSubtitleText(Subtitle.Kind.SECONDARY, msg.value());
            case "sub-start" ->
              handleSubtitleStartTime(Subtitle.Kind.PRIMARY, msg.value());
            case "secondary-sub-start" ->
              handleSubtitleStartTime(Subtitle.Kind.SECONDARY, msg.value());
            default ->
              LOG.debug("Did not handle mpv property change: {}", msg.name());
          }
        }
        case MPVMessage.EndFile _ ->
          gotQuitMessage = true;
        case MPVMessage.Unrecognized _ ->
          LOG.trace("Did not handle an unrecognized mpv message");
      }
    }

    if (statusUpdate != null) {
      statusUpdateCb.accept(statusUpdate);
    }

    return gotQuitMessage;
  }

  private void handleSubtitleText(Subtitle.Kind kind, String text) {
    if (text == null) {
      return;
    }
    var processedText = text.substring(1, text.length() - 1); // Remove quotemarks
    if (processedText.isEmpty()) {
      return;
    }

    processedText = ESCAPED_NEWLINE_RE.matcher(processedText).replaceAll("\n");
    processedText = ESCAPED_QUOTEMARK_RE.matcher(processedText).replaceAll("\"");

    pendingSubtitleTexts[kind.ordinal()] = processedText;
  }

  private void handleSubtitleStartTime(Subtitle.Kind kind, String timeStr) {
    if (timeStr == null) {
      return;
    }
    var pending = pendingSubtitleTexts[kind.ordinal()];
    if (pending != null) {
      var time = Double.parseDouble(timeStr);
      subtitleCb.accept(new Subtitle(kind, pending, time));
      pendingSubtitleTexts[kind.ordinal()] = null;
    } else {
      LOG.warn("Received subtitle time but there was no pending text");
    }
  }

  private static String ipcJSONToPrintable(String json) {
    return json.replace("\n", "\\n");
  }

  protected abstract class BaseWorker implements Runnable {
    final Function<String, Boolean> messagesCb;

    BaseWorker(Function<String, Boolean> messagesCb) {
      this.messagesCb = messagesCb;
    }

    @Override
    public void run() {
      LOG.debug("Waiting for mpv connection");
      connect();
      state = State.CONNECTED;

      // The external world will be notified of the established connection when we handle the
      // incoming pause status update
      sendCommand(new MPVCommand.ObserveProperty("pause"));

      sendCommand(new MPVCommand.ObserveProperty("sub-text"));
      sendCommand(new MPVCommand.ObserveProperty("sub-start"));
      sendCommand(new MPVCommand.ObserveProperty("secondary-sub-text"));
      sendCommand(new MPVCommand.ObserveProperty("secondary-sub-start"));

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
