package io.github.kamitejp.platform.linux.wlroots.dependencies.wlrctl;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;

public final class Wlrctl extends BaseSimpleDependency {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Wlrctl() {
    super("wlrctl");
  }

  @Override
  public boolean checkIsAvailable() {
    var res = ProcessHelper.run(
      ProcessRunParams.ofCmd(BIN, "-h").withTimeout(DEFAULT_AVAILABILITY_CHECK_TIMEOUT_MS)
    );
    if (!res.didComplete() || !res.getStdout().startsWith("Usage: wlrctl")) {
      LOG.debug("wlrctl failed to respond with the expected usage message");
      return false;
    }
    return true;
  }

  public WlrctlResult mouseClick() {
    var cmd = List.of(BIN, "pointer", "click");
    var res = ProcessHelper.runWithBinaryOutput(ProcessRunParams.ofCmd(cmd).withTimeout(1000));
    if (!res.didComplete()) {
      return new WlrctlResult.ExecutionFailed();
    } else if (res.didCompleteWithError()) {
      return new WlrctlResult.Error(res.getStderr());
    }
    return new WlrctlResult.Ok();
  }
}
