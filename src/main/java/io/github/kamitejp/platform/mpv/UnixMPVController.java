package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class UnixMPVController extends BaseMPVController {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final UnixDomainSocketAddress SOCKET_ADDR =
    UnixDomainSocketAddress.of("/tmp/%s".formatted(IPC_MEDIUM_FILENAME));
  private static final int READ_BUFFER_CAPACITY = 8192;

  private SocketChannel socketChannel;

  protected UnixMPVController() {
    var workerThread = new Thread(new Worker(this::handleMessages));
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

  private class Worker extends BaseMPVController.Worker {
    private final ByteBuffer readBuffer;

    Worker(Function<String, Boolean> messagesCb) {
      super(messagesCb);
      this.readBuffer = ByteBuffer.allocate(READ_BUFFER_CAPACITY);
    }

    @Override
    protected boolean connect() {
      // This will block until we connect
      socketChannel = waitForConnection();
      if (socketChannel == null) {
        LOG.error("Received a null UNIX socket channel. Aborting");
        return false;
      }
      LOG.info("Connected to mpv UNIX socket at {}", SOCKET_ADDR);
      return true;
    }

    private SocketChannel waitForConnection() {
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

    protected void runReader() {
      try {
        while (true) {
          // This blocks if the connection is alive, otherwise it returns null
          var msgs = read();
          if (msgs != null) {
            messagesCb.apply(msgs);
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
