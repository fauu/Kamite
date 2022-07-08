// Adapted from USGS Product Distribution Layer - https://github.com/usgs/pdl (Public Domain)

package io.github.kamitejp.platform.process;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class TimeoutProcessBuilder {
  private ProcessBuilder builder = null;
  private long timeoutMS = -1;

  public TimeoutProcessBuilder(long timeout, String... command) {
    builder = new ProcessBuilder(command);
    timeoutMS = timeout;
  }

  public TimeoutProcessBuilder(long timeout, List<String> command) {
    builder = new ProcessBuilder(command);
    timeoutMS = timeout;
  }

  public List<String> command() {
    return builder.command();
  }

  public TimeoutProcessBuilder command(List<String> command) {
    builder.command(command);
    return this;
  }

  public TimeoutProcessBuilder command(String command) {
    builder.command(command);
    return this;
  }

  public File directory() {
    return builder.directory();
  }

  public TimeoutProcessBuilder directory(File directory) {
    builder.directory(directory);
    return this;
  }

  public Map<String, String> environment() {
    return builder.environment();
  }

  public boolean redirectErrorStream() {
    return builder.redirectErrorStream();
  }

  public TimeoutProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
    builder.redirectErrorStream(redirectErrorStream);
    return this;
  }

  public TimeoutProcess start() throws IOException {
    var process = new TimeoutProcess(builder.start());
    if (timeoutMS > 0) {
      final Timer timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          process.setTimeoutElapsed(true);
          process.destroy();
        }
      }, timeoutMS);
      process.setTimer(timer);
    }
    return process;
  }

  public long getTimeout() {
    return this.timeoutMS;
  }

  public void setTimeout(final long timeout) {
    this.timeoutMS = timeout;
  }
}

