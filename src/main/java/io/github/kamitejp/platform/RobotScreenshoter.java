package io.github.kamitejp.platform;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.Optional;

public class RobotScreenshoter {
  private final Robot robot;

  public RobotScreenshoter() throws RobotScreenshoterUnavailableException {
    try {
      this.robot = new Robot();
    } catch (AWTException e) {
      throw new RobotScreenshoterUnavailableException(e);
    }
  }

  public Optional<BufferedImage> takeScreenshotOfArea(Rectangle area) {
    return Optional.of(this.robot.createScreenCapture(area));
  }

  public Optional<BufferedImage> takeScreenshotAround(Point point, int areaWidth) {
    int halfwidth = areaWidth / 2;
    @SuppressWarnings("SuspiciousNameCombination") var area =
      new Rectangle(point.x - halfwidth, point.y - halfwidth, areaWidth, areaWidth);
    return takeScreenshotOfArea(area);
  }
}
