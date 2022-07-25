package io.github.kamitejp.platform.mpv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.status.PlayerStatus;

public final class WindowsMPVController extends AbstractMPVController {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PIPE_ADDR = "\\\\.\\pipe\\%s".formatted(PIPE_FILENAME);
  private static final int READ_POLL_INTERVAL_MS = 333;
  private static final int MAX_EXPECTED_RESPONSE_TIME_MS = 33;

  private RandomAccessFile pipeFile;
  private Thread workerThread;

  protected WindowsMPVController(Consumer<PlayerStatus> statusUpdateCb) {
    super(statusUpdateCb);

    workerThread = new Thread(new Worker(this::handleMessage));
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

  private class Worker implements Runnable {
    private final Function<String, Boolean> messageCb;

    Worker(Function<String, Boolean> messageCb) {
      this.messageCb = messageCb;
    }

    @Override
    public void run() {
      LOG.debug("Waiting for mpv connection");
      // This will block until we connect to the pipe
      pipeFile = waitForConnection();
      if (pipeFile == null) {
        LOG.error("Received a null pipe file. Aborting");
        return;
      }
      state = State.CONNECTED;
      LOG.info("Connected to mpv pipe at {}", PIPE_ADDR);

      // The external world will be notified of the established connection as we handle the incoming
      // pause status update
      sendCommand(MPVCommand.OBSERVE_PAUSE);

      // This will block until the reader is asked to finish
      runReader();

      statusUpdateCb.accept(PlayerStatus.DISCONNECTED);
      state = State.NOT_CONNECTED;
      LOG.info("mpv disconnected");

      try {
        Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting for mpv connection to close. Aborting");
        return;
      }

      // Wait for a new connection
      run();
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
        if (LOG.isTraceEnabled()) {
          LOG.trace("Could not connect to mpv named pipe at {}: {}", PIPE_ADDR, e.getMessage());
        }
        return null;
      }
    }

    private void runReader() {
      try {
        while (true) {
          // This blocks if there is nothing to read
          var msg = read();
          if (msg != null) {
            var shouldFinish = messageCb.apply(msg);
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
