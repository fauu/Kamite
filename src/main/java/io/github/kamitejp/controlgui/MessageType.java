package io.github.kamitejp.controlgui;

import java.awt.Color;
import java.util.Optional;

import org.apache.logging.log4j.spi.StandardLevel;

public enum MessageType {
  INFO("Info", ""),
  WARNING("Warning", colorToHexString(ControlGUI.COLOR_WARNING)),
  ERROR("Error", colorToHexString(ControlGUI.COLOR_ERROR2));

  private String displayString;
  private String colorHex;

  MessageType(String displayString, String colorHex) {
    this.displayString = displayString;
    this.colorHex = colorHex;
  }

  public String getColorHex() {
    return this.colorHex;
  }

  @Override
  public String toString() {
    return displayString;
  }

  public static Optional<MessageType> fromLog4jStandardLevel(StandardLevel level) {
    return Optional.ofNullable(switch (level) {
      case INFO  -> MessageType.INFO;
      case WARN  -> MessageType.WARNING;
      case ERROR -> MessageType.ERROR;
      default -> null;
    });
  }

  // QUAL: Doesn't belong here
  private static String colorToHexString(Color color) {
    return "#%s".formatted(Integer.toHexString(color.getRGB()).substring(2));
  }
}
