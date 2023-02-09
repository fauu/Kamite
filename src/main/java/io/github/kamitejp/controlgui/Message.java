package io.github.kamitejp.controlgui;

import javax.swing.JLabel;

public class Message extends JLabel {
  public Message(String timeString, Type type, String content) {
    super("<html>%s <i>%s</i> %s</html>".formatted(timeString, type, content));
  }

  public static enum Type {
    INFO("Info"),
    WARNING("Warning"),
    ERROR("Error");

    private String displayString;

    private Type(String displayString) {
      this.displayString = displayString;
    }

    @Override
    public String toString() {
      return displayString;
    }
  }
}
