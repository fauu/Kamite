package io.github.kamitejp.platform.mpv;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WindowsMPVController extends BaseMPVController {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PIPE_NAME = "\\\\.\\pipe\\%s".formatted(IPC_MEDIUM_FILENAME);
  private static final Path PIPE_PATH = Paths.get(PIPE_NAME);
  private static final int READ_BUFFER_CAPACITY = 8192;

  private AsynchronousFileChannel pipeChannel;
  private final Thread workerThread;

  WindowsMPVController() {
    workerThread = new Thread(new Worker(this::handleMessages));
    LOG.debug("Starting mpv controller worker thread");
    workerThread.start();
  }

  @Override
  protected void sendBytes(byte[] bytes) throws IOException {
    pipeChannel.write(ByteBuffer.wrap(bytes), 0);
  }

  public void sendCommand(MPVCommand cmd) {
    super.sendCommand(cmd);
  }

  public void destroy() {
    if (pipeChannel != null) {
      try {
        pipeChannel.close();
      } catch (IOException e) {
        LOG.error("Failed to close mpv named pipe connection", e);
      }
    }
  }

  private class Worker extends BaseMPVController.BaseWorker {
    private final ByteBuffer readBuffer;
    private final CharBuffer charBuffer;
    private final CharsetDecoder charsetDecoder;

    Worker(Function<String, Boolean> messagesCb) {
      super(messagesCb);
      readBuffer = ByteBuffer.allocate(READ_BUFFER_CAPACITY);
      charBuffer = CharBuffer.allocate(READ_BUFFER_CAPACITY);
      charsetDecoder = StandardCharsets.UTF_8.newDecoder();
    }

    @Override
    protected boolean connect() {
      // This will block until we connect
      pipeChannel = waitForConnection();
      if (pipeChannel == null) {
        LOG.error("Received a null pipe. Aborting");
        return false;
      }
      LOG.info("Connected to mpv pipe at {}", PIPE_NAME);
      return true;
    }

    private static AsynchronousFileChannel waitForConnection() {
      try {
        while (true) {
          var pipeChannel = tryConnect();
          if (pipeChannel != null) {
            return pipeChannel;
          }
          //noinspection BusyWait
          Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
        }
      } catch (InterruptedException e) {
        LOG.debug("Connecter thread was interrupted. Aborting", e);
      }
      return null;
    }

    private static AsynchronousFileChannel tryConnect() {
      try {
        return AsynchronousFileChannel.open(
          PIPE_PATH, StandardOpenOption.READ, StandardOpenOption.WRITE
        );
      } catch (IOException e) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Could not connect to mpv named pipe at {}: {}", PIPE_NAME, e.getMessage());
        }
        return null;
      }
    }

    protected void runReader() {
      try {
        while (true) {
          // This blocks if there is nothing to read
          var msgs = read();
          if (msgs != null) {
            var shouldFinish = messagesCb.apply(msgs);
            if (shouldFinish) {
              LOG.debug("Finishing mpv IPC reader as requested");
              break;
            }
          } else {
            LOG.debug("Read `null` from mpv pipe");
            return;
          }
        }
      } catch(IOException | InterruptedException | ExecutionException e) {
        LOG.error("Error when reading from mpv pipe. See stderr for the stack trace");
        e.printStackTrace();
      }
    }

    private String read() throws IOException, InterruptedException, ExecutionException {
      pipeChannel.read(readBuffer, 0).get();
      readBuffer.flip();
      charsetDecoder.decode(readBuffer, charBuffer, true);
      charBuffer.flip();
      var res = charBuffer.toString();
      readBuffer.clear();
      charBuffer.clear();
      return res;
    }
  }
}
