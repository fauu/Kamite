package io.github.kamitejp.platform.linux.wlroots.dependencies.grim;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;
import io.github.kamitejp.image.ImageOps;

public final class Grim extends BaseSimpleDependency {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Grim() {
    super("grim");
  }

  @Override
  public boolean checkIsAvailable() {
    var res = ProcessHelper.run(
      ProcessRunParams.ofCmd(BIN, "-h").withTimeout(DEFAULT_AVAILABILITY_CHECK_TIMEOUT_MS)
    );
    if (!res.didComplete() || !res.getStdout().startsWith("Usage: grim")) {
      LOG.debug("grim failed to respond with the expected usage message");
      return false;
    }
    if (!(takeFullScreenshot() instanceof GrimResult.Screenshot)) {
      LOG.debug("grim failed to take a test screenshot");
      return false;
    }
    return true;
  }

  public GrimResult takeFullScreenshot() {
    return takeScreenshot(null);
  }

  public GrimResult takeScreenshotOfSlurpSelection(String selection) {
    return takeScreenshot(selection);
  }

  private GrimResult takeScreenshot(String selection) {
    var cmd = new ArrayList<String>();
    cmd.add(BIN);
    if (selection != null) {
      cmd.add("-g");
      cmd.add(selection);
    }
    cmd.add("-");

    var res = ProcessHelper.runWithBinaryOutput(ProcessRunParams.ofCmd(cmd).withTimeout(3000));
    if (!res.didComplete()) {
      return new GrimResult.ExecutionFailed();
    } else if (res.didCompleteWithError()) {
      return new GrimResult.Error(res.getStderr());
    }
    return new GrimResult.Screenshot(ImageOps.fromBytes(res.getStdout()));
  }
}
