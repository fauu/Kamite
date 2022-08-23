package io.github.kamitejp.platform.mpv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WindowsMPVController extends BaseMPVController {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PIPE_ADDR = "\\\\.\\pipe\\%s".formatted(IPC_MEDIUM_FILENAME);
  private static final int READ_POLL_INTERVAL_MS = 125;
  private static final int MAX_EXPECTED_RESPONSE_TIME_MS = 33;

  private RandomAccessFile pipeFile;
  private Thread workerThread;

  protected WindowsMPVController() {
    workerThread = new Thread(new Worker(this::handleMessages));
    LOG.debug("Starting mpv controller worker thread");
    workerThread.start();
  }

  @Override
  protected void sendBytes(byte[] bytes) throws IOException {
    pipeFile.write(bytes);
  }

  public void sendCommand(MPVCommand cmd) {
    super.sendCommand(cmd);
    // Sending a command might cause prompt response, so we interrupt to tell the reader to not
    // wait the full poll interval this once before checking for incoming messages
    workerThread.interrupt();
  }

  public void destroy() {
    if (pipeFile != null) {
      try {
        pipeFile.close();
      } catch (IOException e) {
        LOG.error("Failed to close mpv named pipe connection", e);
      }
    }
  }

  @Override
  protected String subtitleTextMidTransform(String text) {
    // NOTE: ???
    var bytes = text.getBytes(StandardCharsets.UTF_16);
    var transformedBytes = new byte[(bytes.length - 2) / 2];
    for (int i = 2, j = 0; i < bytes.length; i++) {
      if (i % 2 == 0) {
        continue;
      }
      transformedBytes[j] = bytes[i];
      j++;
    }
    return new String(transformedBytes, StandardCharsets.UTF_8);
  }

  private class Worker extends BaseMPVController.Worker {
    Worker(Function<String, Boolean> messagesCb) {
      super(messagesCb);
    }

    @Override
    protected boolean connect() {
      // This will block until we connect
      pipeFile = waitForConnection();
      if (pipeFile == null) {
        LOG.error("Received a null pipe. Aborting");
        return false;
      }
      LOG.info("Connected to mpv pipe at {}", PIPE_ADDR);
      return true;
    }

    private RandomAccessFile waitForConnection() {
      try {
        while (true) {
          var pipe = tryConnect();
          if (pipe != null) {
            return pipe;
          }
          //noinspection BusyWait
          Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
        }
      } catch (InterruptedException e) {
        LOG.debug("Connecter thread was interrupted. Aborting", e);
      }
      return null;
    }

    private static RandomAccessFile tryConnect() {
      try {
        return new RandomAccessFile(PIPE_ADDR, "rw");
      } catch (FileNotFoundException e) {
        LOG.trace("Could not connect to mpv named pipe at {}: {}", PIPE_ADDR, () -> e.getMessage());
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
      } catch(IOException e) {
        LOG.error("Error when reading from mpv pipe. See stderr for the stack trace");
        e.printStackTrace();
      }
    }

    private String read() throws IOException {
      // pipe.readLine() blocks here in a way that it's impossible to write to the pipe until
      // something is read from it. Therefore, to be able to not just read but also write at will,
      // we need to poll until there's something to read and only then call readLine().
      while (pipeFile.length() <= 0) {
        try {
          Thread.sleep(READ_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          // This interrupt means that we expect an incoming command response message shortly
          try {
            Thread.sleep(MAX_EXPECTED_RESPONSE_TIME_MS);
          } catch (InterruptedException e1) {
            // Ignore
          }
        }
      }
      return pipeFile.readLine();
    }
  }
}
