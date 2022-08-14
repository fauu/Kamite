package io.github.kamitejp.platform.process;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessHelper {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ProcessHelper() {}

  public static ProcessResult<String> run(String... cmd) {
    return run(ProcessRunParams.ofCmd(cmd));
  }

  public static ProcessResult<String> run(List<String> cmd) {
    return run(ProcessRunParams.ofCmd(cmd));
  }

  public static ProcessResult<String> run(ProcessRunParams params) {
    LOG.debug("Running: `{}`", params.getCmdString()); // NOPMD
    var pb = initProcessBuilder(params);
    try {
      var process = pb.start();
      if (params.getInputBytes() != null) {
        process.getOutputStream().write(params.getInputBytes());
      }
      process.getOutputStream().close();
      var out = ProcessHelper.readStdoutAndStderr(process);
      return ProcessResult.completed(process.waitFor(), out.stdout(), out.stderr());
    } catch (ProcessTimeoutException e) {
      LOG.debug("Process '{}' has timed out", params.getCmd()[0]);
      return ProcessResult.timedOut();
    } catch (Exception e) {
      LOG.debug("Exception while running process '{}': {}", params.getCmd()[0], e);
      return ProcessResult.failedToExecute();
    }
  }

  public static ProcessResult<byte[]> runWithBinaryOutput(String... cmd) {
    return runWithBinaryOutput(ProcessRunParams.ofCmd(cmd));
  }

  public static ProcessResult<byte[]> runWithBinaryOutput(List<String> cmd) {
    return runWithBinaryOutput(ProcessRunParams.ofCmd(cmd));
  }

  public static ProcessResult<byte[]> runWithBinaryOutput(ProcessRunParams params) {
    LOG.debug("Running with binary output: `{}`", params.getCmdString()); // NOPMD
    var pb = initProcessBuilder(params);
    try {
      var process = pb.start();
      process.getOutputStream().close();
      var out = ProcessHelper.readStdoutBinaryAndStderr(process);
      return ProcessResult.completed(process.waitFor(), out.stdout(), out.stderr());
    } catch (Exception e) {
      LOG.debug("Exception while running process '{}': {}", params.getCmd()[0], e);
      return ProcessResult.failedToExecute();
    }
  }

  public static String readStdout(TimeoutProcess process) {
    if (process.timeoutElapsed()) {
      return "";
    }
    try {
      return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch(IOException e) {
      LOG.error("Exception while reading Process's stdout as string", e);
      return null;
    }
  }

  public static byte[] readStdoutBinary(TimeoutProcess process) {
    if (process.timeoutElapsed()) {
      return new byte[0];
    }
    try {
      return process.getInputStream().readAllBytes();
    } catch (IOException e) {
      LOG.error("Exception while reading Process's stderr as string", e);
      return new byte[0];
    }
  }

  public static String readStderr(TimeoutProcess process) {
    if (process.timeoutElapsed()) {
      return "";
    }
    try {
      return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch(IOException e) {
      LOG.error("Exception while reading Process's stderr as string", e);
      return "";
    }
  }

  public record StringOutput(String stdout, String stderr) {}

  public static StringOutput readStdoutAndStderr(TimeoutProcess process) {
    // ASSUMPTION: Process does not output to stderr before stdout. Otherwise, we'll deadlock.
    return new StringOutput(readStdout(process), readStderr(process));
  }

  public record BinaryAndStringOutput(byte[] stdout, String stderr) {}

  public static BinaryAndStringOutput readStdoutBinaryAndStderr(TimeoutProcess process) {
    // ASSUMPTION: Process does not output to stderr before stdout. Otherwise, we'll deadlock.
    return new BinaryAndStringOutput(readStdoutBinary(process), readStderr(process));
  }

  private static TimeoutProcessBuilder initProcessBuilder(ProcessRunParams params) {
    var pb = new TimeoutProcessBuilder(params.getTimeout(), params.getCmd());
    if (params.getEnv() != null) {
      pb.environment().putAll(params.getEnv());
    }
    return pb;
  }
}
