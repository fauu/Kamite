package io.github.kamitejp.platform.process;

import java.util.List;
import java.util.Map;

public final class ProcessRunParams {
  private final String[] cmd;
  private long timeout = 0;
  private Map<String, String> env;
  private byte[] inputBytes = null;

  private ProcessRunParams(String... cmd) {
    this.cmd = cmd;
  }

  public static ProcessRunParams ofCmd(String... cmd) {
    return new ProcessRunParams(cmd);
  }

  public static ProcessRunParams ofCmd(List<String> cmd) {
    return new ProcessRunParams(cmd.toArray(new String[0]));
  }

  public ProcessRunParams withTimeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  public ProcessRunParams withEnv(Map<String, String> env) {
    this.env = env;
    return this;
  }

  public ProcessRunParams withInputBytes(byte[] inputBytes) {
    this.inputBytes = inputBytes;
    return this;
  }

  public long getTimeout() {
    return timeout;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public byte[] getInputBytes() {
    return inputBytes;
  }

  public String[] getCmd() {
    return cmd;
  }

  public String getCmdString() {
    return String.join(" ", cmd);
  }
}
