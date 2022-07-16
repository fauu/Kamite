package io.github.kamitejp.platform.linux.wlroots.dependencies.slurp;

import java.util.ArrayList;
import java.util.regex.Pattern;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;

public final class Slurp extends BaseSimpleDependency {
  private static final Pattern AREA_RE =
    Pattern.compile("(?<x>\\d+),(?<y>\\d+) (?<w>\\d+)x(?<h>\\d+)");

  public Slurp() {
    super("slurp");
  }

  @Override
  public boolean checkIsAvailable() {
    var res = ProcessHelper.run(ProcessRunParams.ofCmd(BIN, "-h").withTimeout(1000));
    return res.didComplete() && res.getStdout().startsWith("Usage: slurp");
  }

  public SlurpResult getSelectionFromUser(SlurpMode mode) {
    var cmd = new ArrayList<String>();
    cmd.add(BIN);
    if (mode == SlurpMode.POINT) {
      cmd.add("-p");
      cmd.add("-f");
      cmd.add("%x %y");
    }

    var res = ProcessHelper.run(cmd);
    if (!res.didComplete()) {
      return new SlurpResult.ExecutionFailed();
    } else if (res.didCompleteAndError()) {
      if ("selection cancelled".equalsIgnoreCase(res.getStderr())) {
        return new SlurpResult.Cancelled();
      }
      return new SlurpResult.Error(res.getStderr());
    } else if (mode == SlurpMode.AREA) {
      var m = AREA_RE.matcher(res.getStdout());
      m.find();
      var x = Integer.parseInt(m.group("x"));
      var y = Integer.parseInt(m.group("y"));
      var width = Integer.parseInt(m.group("w"));
      var height = Integer.parseInt(m.group("h"));
      return new SlurpResult.Area(Rectangle.ofStartAndDimensions(x, y, width, height));
    }

    var coords = res.getStdout().split(" ");
    if (coords.length != 2) {
      return new SlurpResult.FormatDifferentFromExpected(
        "two segments separated by a space"
      );
    }

    int x;
    int y;
    try {
      x = Integer.parseInt(coords[0]);
      y = Integer.parseInt(coords[1]);
    } catch (NumberFormatException e) {
      return new SlurpResult.FormatDifferentFromExpected("integers");
    }
    return new SlurpResult.Point(new Point(x, y));
  }
}
