package io.github.kamitejp.controlgui;

import java.awt.Color;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class Message extends JPanel {
  private static final String MAIN_LABEL_FORMAT = "<html>%s%s</html>";
  private static final String MAIN_LABEL_TYPE_PART_FORMAT = "<font color='%s'><b>%s</b></font> ";

  private static final int DEF = GroupLayout.DEFAULT_SIZE;
  private static final int PREF = GroupLayout.PREFERRED_SIZE;
  private static final int MAX = Short.MAX_VALUE;

  public Message(String timeString, Type type, String content) {
    var layout = new GroupLayout(this);
    setLayout(layout);
    setBackground(ControlGUI.COLOR_BG2);

    var timeLabel = new JLabel(timeString);
    timeLabel.setFont(ControlGUI.getFontMonospacedDefault());
    var mainLabel = new JLabel(MAIN_LABEL_FORMAT.formatted(mainLabelTypePart(type), content));

    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addComponent(timeLabel, PREF, PREF, PREF)
        .addGap(ControlGUI.MESSAGE_HORIZONTAL_GAP)
        .addComponent(mainLabel, DEF, DEF, MAX)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(Alignment.LEADING, false)
        .addComponent(timeLabel, PREF, PREF, PREF)
        .addGap(ControlGUI.MESSAGE_VERTICAL_GAP)
        .addComponent(mainLabel, PREF, PREF, MAX)
        .addGap(ControlGUI.MESSAGE_VERTICAL_GAP)
    );
  }

  private String mainLabelTypePart(Type type) {
    if (type == Type.INFO) {
      return "";
    }
    return MAIN_LABEL_TYPE_PART_FORMAT.formatted(type.getColorHex(), type.toString());
  }

  public static enum Type {
    INFO("Info", ""),
    WARNING("Warning", colorToHexString(ControlGUI.COLOR_WARNING)),
    ERROR("Error", colorToHexString(ControlGUI.COLOR_ERROR2));

    private String displayString;
    private String colorHex;

    private Type(String displayString, String colorHex) {
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

    // QUAL: Doesn't belong here
    private static String colorToHexString(Color color) {
      return "#%s".formatted(Integer.toHexString(color.getRGB()).substring(2));
    }
  }
}
