package io.github.kamitejp.controlgui.ui;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JLabel;
import javax.swing.JPanel;

import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.controlgui.MessageType;

public class Message extends JPanel {
  private static final String MAIN_LABEL_FORMAT = "<html>%s%s</html>";
  private static final String MAIN_LABEL_TYPE_PART_FORMAT = "<font color='%s'><b>%s</b></font> ";

  private static final int DEF = GroupLayout.DEFAULT_SIZE;
  private static final int PREF = GroupLayout.PREFERRED_SIZE;
  private static final int MAX = Short.MAX_VALUE;

  public Message(String timeString, MessageType type, String content) {
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

  private String mainLabelTypePart(MessageType type) {
    if (type == MessageType.INFO) {
      return "";
    }
    return MAIN_LABEL_TYPE_PART_FORMAT.formatted(type.getColorHex(), type.toString());
  }
}
