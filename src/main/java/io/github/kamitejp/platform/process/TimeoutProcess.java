// Adapted from USGS Product Distribution Layer - https://github.com/usgs/pdl (Public Domain)

package io.github.kamitejp.platform.process;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;

public class TimeoutProcess {
  private final Process process;
  private boolean timeoutElapsed = false;
  private Timer timer = null;

  protected TimeoutProcess(Process process) {
    this.process = process;
  }

  public void destroy() {
    process.destroy();
  }

  public int exitValue() {
    return process.exitValue();
  }

  public InputStream getErrorStream() {
    return process.getErrorStream();
  }

  public InputStream getInputStream() {
    return process.getInputStream();
  }

  public OutputStream getOutputStream() {
    return process.getOutputStream();
  }

  public int waitFor() throws InterruptedException, ProcessTimeoutException {
    var status = -1;
    try {
      status = process.waitFor();
      if (timeoutElapsed()) {
        throw new ProcessTimeoutException();
      }
    } finally {
      if (timer != null) {
        timer.cancel();
      }
    }
    return status;
  }

  protected void setTimeoutElapsed(boolean timeoutElapsed) {
    this.timeoutElapsed = timeoutElapsed;
  }

  protected boolean timeoutElapsed() {
    return timeoutElapsed;
  }

  protected void setTimer(final Timer timer) {
    this.timer = timer;
  }
}

