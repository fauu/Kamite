package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.platform.OSFamily;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.status.PlayerStatus;

public final class MPVController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int CONNECTION_RETRY_INTERVAL_MS = 2000;
  private static final UnixDomainSocketAddress UNIX_SOCKET_ADDR =
    UnixDomainSocketAddress.of("/tmp/kamite-mpvsocket");
  private static final int GET_PAUSE_REQUEST_ID = 100;

  private static final String OUT_COMMAND_PLAYPAUSE = "\"cycle\", \"pause\"]";
  private static final String OUT_COMMAND_SEEK_BACK = "\"seek\", -1, \"exact\"]";
  private static final String OUT_COMMAND_SEEK_FORWARD = "\"seek\", 1, \"exact\"]";
  private static final String OUT_COMMAND_SEEK_START_SUB = "\"sub-seek\", 0]";
  private static final String OUT_COMMAND_GET_PAUSE = "\"get_property\", \"pause\"]";

  private static final String IN_MESSAGE_PAUSE = "\"pause\"";
  private static final String IN_MESSAGE_UNPAUSE = "\"unpause\"";
  private static final String IN_MESSAGE_PAUSE_RESPONSE =
    "\"request_id\":%s".formatted(GET_PAUSE_REQUEST_ID);

  private Consumer<PlayerStatus> statusUpdateCb;
  private State state;
  private SocketChannel linuxEndpointSocketChannel;

  public MPVController(Platform platform, Consumer<PlayerStatus> statusUpdateCb) {
    if (platform.getOSFamily() != OSFamily.UNIX) {
      LOG.error("mpv controller is not supported on the current platform");
      return;
    }

    this.statusUpdateCb = statusUpdateCb;
    state = State.NOT_CONNECTED;

    var workerThread = new Thread(new Worker(this::handleMessage));
    LOG.debug("Starting mpv controller worker thread");
    workerThread.start();
  }

  public void sendCommand(MPVCommand cmd) {
    if (linuxEndpointSocketChannel == null) {
      return;
    }

    var ipcCmd = "{\"command\": ["
      + switch (cmd) {
        case PLAYPAUSE      -> OUT_COMMAND_PLAYPAUSE;
        case SEEK_BACK      -> OUT_COMMAND_SEEK_BACK;
        case SEEK_FORWARD   -> OUT_COMMAND_SEEK_FORWARD;
        case SEEK_START_SUB -> OUT_COMMAND_SEEK_START_SUB;
        case GET_PAUSE ->
          "%s, \"request_id\": %d".formatted(OUT_COMMAND_GET_PAUSE, GET_PAUSE_REQUEST_ID);
      }
      + "}\n";
    try {
      linuxEndpointSocketChannel.write(ByteBuffer.wrap(ipcCmd.getBytes(StandardCharsets.UTF_8)));
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sent mpv IPC command: {}", ipcCmd.replace("\n", "\\n"));
      }
    } catch (IOException e) {
      LOG.error("Exception while writing to mpv UNIX socket: ", e);
    }
  }

  public enum State {
    CONNECTED,
    NOT_CONNECTED
  }

  public State getState() {
    return state;
  }

  public void destroy() {
    if (linuxEndpointSocketChannel != null) {
      try {
        linuxEndpointSocketChannel.close();
      } catch (IOException e) {
        LOG.error("Exception while closing mpv UNIX socket connection", e);
      }
    }
  }

  private void handleMessage(String msg) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Received mpv message: {}", msg.replace("\n", "\\n"));
    }

    PlayerStatus update = null;
    if (msg.contains(IN_MESSAGE_PAUSE)) {
      update = PlayerStatus.PAUSED;
    } else if (msg.contains(IN_MESSAGE_UNPAUSE)) {
      update = PlayerStatus.PLAYING;
    } else if (msg.contains(IN_MESSAGE_PAUSE_RESPONSE)) {
      if (msg.contains("true")) {
        update = PlayerStatus.PAUSED;
      } else  {
        update = PlayerStatus.PLAYING;
      }
    }

    if (update != null) {
      statusUpdateCb.accept(update);
    }
  }

  private class Worker implements Runnable {
    private final Consumer<String> messageCb;

    Worker(Consumer<String> messageCb) {
      this.messageCb = messageCb;
    }

    @Override
    public void run() {
      LOG.debug("Waiting for mpv IPC connection");
      // This will block until we connect to the socket
      var socketChannelFuture = CompletableFuture.supplyAsync(new Connecter());
      try {
        linuxEndpointSocketChannel = socketChannelFuture.get();
      } catch (ExecutionException | InterruptedException e) {
        LOG.error("Exception while waiting for mpv IPC Connecter", e);
        return;
      }

      // The external world will be notified of the established connection as we handle the
      // response to the following command
      sendCommand(MPVCommand.GET_PAUSE);
      state = State.CONNECTED;
      LOG.info("Connected to mpv UNIX socket at {}", UNIX_SOCKET_ADDR);

      var readerFuture = CompletableFuture.runAsync(
              new Reader(linuxEndpointSocketChannel, messageCb)
      );
      try {
        readerFuture.get();
      } catch (ExecutionException | InterruptedException e) {
        LOG.error("Exception while waiting for mpv IPC Reader", e);
        return;
      }
      statusUpdateCb.accept(PlayerStatus.DISCONNECTED);
      state = State.NOT_CONNECTED;
      LOG.info("mpv IPC connection closed");

      // Wait for a new connection
      run();
    }
  }

  private static class Connecter implements Supplier<SocketChannel> {
    @Override
    public SocketChannel get() {
      try {
        while(true) {
          var channel = openConnection();
          if (channel != null) {
            return channel;
          }
          //noinspection BusyWait
          Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException("InterruptedException in mpv controller connecter", e);
      }
    }

    private static SocketChannel openConnection() {
      try {
        return SocketChannel.open(UNIX_SOCKET_ADDR);
      } catch (IOException e) {
        if (LOG.isTraceEnabled()) {
          LOG.trace(
            "Could not connect to mpv UNIX socket at {}: {}",
            UNIX_SOCKET_ADDR,
            e.getMessage()
          );
        }
        return null;
      }
    }
  }

  private static class Reader implements Runnable {
    private final SocketChannel channel;
    private final ByteBuffer buffer;
    private final Consumer<String> messageCb;

    Reader(SocketChannel channel, Consumer<String> messageCb) {
      this.channel = channel;
      buffer = ByteBuffer.allocate(1024);
      this.messageCb = messageCb;
    }
    
    @Override
    public void run() {
      try {
        while (true) {
          // This blocks if the connection is alive, otherwise it returns null
          var msg = read();
          if (msg != null) {
            messageCb.accept(msg);
          } else {
            LOG.debug("Could not read from mpv socket");
            // Connection closed
            return;
          }
        }
      } catch(IOException e) {
        LOG.error("Error when reading from mpv socket channel. See stderr for the stack trace");
        e.printStackTrace();
      }
    }

    private String read() throws IOException {
      var bytesRead = channel.read(buffer);
      if (bytesRead < 0) {
        return null;
      }
      var bytes = new byte[bytesRead];
      buffer.flip();
      buffer.get(bytes);
      buffer.clear();
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
