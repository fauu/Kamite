package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.status.PlayerStatus;

public final class UnixMPVController extends AbstractMPVController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final UnixDomainSocketAddress SOCKET_ADDR =
    UnixDomainSocketAddress.of("/tmp/%s".formatted(PIPE_FILENAME));
  private static final int READ_BUFFER_CAPACITY = 512;

  private SocketChannel socketChannel;

  protected UnixMPVController(Consumer<PlayerStatus> statusUpdateCb) {
    super(statusUpdateCb);

    var workerThread = new Thread(new Worker(this::handleMessage));
    LOG.debug("Starting mpv controller worker thread");
    workerThread.start();
  }

  @Override
  protected void sendBytes(byte[] bytes) throws IOException {
    socketChannel.write(ByteBuffer.wrap(bytes));
  }

  @Override
  public void destroy() {
    if (socketChannel != null) {
      try {
        socketChannel.close();
      } catch (IOException e) {
        LOG.error("Failed to close mpv domain socket connection", e);
      }
    }
  }

  private class Worker implements Runnable {
    private final Consumer<String> messageCb;
    private final ByteBuffer readBuffer;

    Worker(Consumer<String> messageCb) {
      this.messageCb = messageCb;
      this.readBuffer = ByteBuffer.allocate(READ_BUFFER_CAPACITY);
    }

    @Override
    public void run() {
      LOG.debug("Waiting for mpv connection");
      // This will block until we connect to the socket
      socketChannel = waitForConnection();
      if (socketChannel == null) {
        LOG.error("Received a null UNIX socket channel. Aborting");
        return;
      }
      state = State.CONNECTED;
      LOG.info("Connected to mpv UNIX socket at {}", SOCKET_ADDR);

      // The external world will be notified of the established connection as we handle the incoming
      // pause status update
      sendCommand(MPVCommand.OBSERVE_PAUSE);

      // This will block until the connection closes
      runReader();

      statusUpdateCb.accept(PlayerStatus.DISCONNECTED);
      state = State.NOT_CONNECTED;
      LOG.info("mpv disconnected");

      // Give a moment for the socket to close. Without this we would immediately connect to the
      // expired socket and cause an exception by trying to write to it
      try {
        Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting for mpv connection to close. Aborting");
        return;
      }

      // Wait for a new connection
      run();
    }

    public SocketChannel waitForConnection() {
      try {
        while (true) {
          var channel = tryConnect();
          if (channel != null) {
            return channel;
          }
          //noinspection BusyWait
          Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
        }
      } catch (InterruptedException e) {
        LOG.debug("Connecter thread was interrupted. Aborting", e);
      }
      return null;
    }

    private static SocketChannel tryConnect() {
      try {
        return SocketChannel.open(SOCKET_ADDR);
      } catch (IOException e) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Could not connect to mpv UNIX socket at {}: {}", SOCKET_ADDR, e.getMessage());
        }
        return null;
      }
    }

    private void runReader() {
      try {
        while (true) {
          // This blocks if the connection is alive, otherwise it returns null
          var msg = read();
          if (msg != null) {
            messageCb.accept(msg);
          } else {
            LOG.debug("Read `null` from mpv socket");
            // Connection closed
            return;
          }
        }
      } catch(IOException e) {
        LOG.error("Error when reading from mpv socket. See stderr for the stack trace");
        e.printStackTrace();
      }
    }

    private String read() throws IOException {
      var bytesRead = socketChannel.read(readBuffer);
      if (bytesRead < 0) {
        return null;
      }
      var bytes = new byte[bytesRead];
      readBuffer.flip();
      readBuffer.get(bytes);
      readBuffer.clear();
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
