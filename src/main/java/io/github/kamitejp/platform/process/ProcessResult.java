package io.github.kamitejp.platform.process;

import java.util.Optional;

public final class ProcessResult<T> {
  private final ExecutionOutcome executionOutcome;
  private final Integer exitStatus;
  private final T stdout;
  private final String stderr;

  private ProcessResult(
    ExecutionOutcome executionOutcome,
    Integer exitStatus,
    T stdout,
    String stderr
  ) {
    this.executionOutcome = executionOutcome;
    this.exitStatus = exitStatus;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public static <T> ProcessResult<T> completed(int exitStatus, T stdout, String stderr) {
    return new ProcessResult<>(ExecutionOutcome.COMPLETED, exitStatus, stdout, stderr);
  }

  public static <T> ProcessResult<T> timedOut() {
    return new ProcessResult<>(ExecutionOutcome.TIMED_OUT, null, null, "");
  }

  public static <T> ProcessResult<T> failedToExecute() {
    return new ProcessResult<>(ExecutionOutcome.FAILED, null, null, "");
  }

  public boolean didComplete() {
    return executionOutcome == ExecutionOutcome.COMPLETED;
  }

  public boolean didTimeOut() {
    return executionOutcome == ExecutionOutcome.TIMED_OUT;
  }

  public boolean didFail() {
    return executionOutcome == ExecutionOutcome.FAILED;
  }

  public boolean didCompleteWithoutError() {
    return didComplete() && exitStatus == 0;
  }

  public boolean didCompleteWithError() {
    return didComplete() && exitStatus != 0;
  }

  public Optional<Integer> getExitStatus() {
    return Optional.ofNullable(exitStatus);
  }

  public T getStdout() {
    return stdout;
  }

  public String getStderr() {
    return stderr;
  }

  private enum ExecutionOutcome {
    FAILED,
    TIMED_OUT,
    COMPLETED;
  }
}
