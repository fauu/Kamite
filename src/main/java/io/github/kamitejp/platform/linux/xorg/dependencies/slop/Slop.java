package io.github.kamitejp.platform.linux.xorg.dependencies.slop;

import java.util.stream.Stream;

import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;

public final class Slop extends BaseSimpleDependency {
  public Slop() {
    super("slop");
  }

  @Override
  public boolean checkIsAvailable() {
    var res = ProcessHelper.run(ProcessRunParams.ofCmd(BIN, "-h").withTimeout(1000));
    return res.didComplete() && res.getStdout().startsWith("slop v");
  }

  public SlopResult getAreaSelectionFromUser() {
    var res = ProcessHelper.run(ProcessRunParams.ofCmd("slop", "-t", "0", "-f", "%x %y %w %h"));
    if (!res.didComplete()) {
      return new SlopResult.ExecutionFailed();
    } else if (res.didCompleteWithError()) {
      if (res.getStderr().contains("Selection was cancelled")) {
        return new SlopResult.Cancelled();
      }
      return new SlopResult.Error(res.getStderr());
    }

    var parts = res.getStdout().split(" ");
    if (parts.length != 4) {
      return new SlopResult.FormatDifferentFromExpected("four segments separated by spaces");
    }
    int[] parsed;
    try {
      parsed = Stream.of(parts).mapToInt(Integer::parseInt).toArray();
    } catch (NumberFormatException e) {
      return new SlopResult.FormatDifferentFromExpected("integers");
    }

    var rect = Rectangle.ofStartAndDimensions(parsed[0], parsed[1], parsed[2], parsed[3]);
    if (rect.getWidth() == 0 || rect.getHeight() == 0) {
      return new SlopResult.ZeroArea();
    }

    return new SlopResult.Area(rect);
  }
}
