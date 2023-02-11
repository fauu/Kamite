package io.github.kamitejp.controlgui;

import java.awt.Color;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class Message extends JPanel {
  private static final int HORIZONTAL_GAP = 10;
  private static final int VERTICAL_GAP = 20;

  private static Integer typeLabelWidth;

  public Message(String timeString, Type type, String content) {
    var layout = new GroupLayout(this);
    setLayout(layout);
    setBackground(new Color(0x484542)); // XXX: Constant in ControlGUI

    if (typeLabelWidth == null) {
      // QUAL: Better to use getStringBounds() or something of the sort?
      typeLabelWidth = (int) new JLabel(Type.WARNING.toString()).getPreferredSize().getWidth();
    }

    var timeLabel = new JLabel(timeString);
    var typeLabel = new JLabel(type.toString());
    var contentLabel = new JLabel("<html>%s</html>".formatted(content));

    var def = GroupLayout.DEFAULT_SIZE;
    var pref = GroupLayout.PREFERRED_SIZE;
    var max = Short.MAX_VALUE;
    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addComponent(timeLabel, pref, pref, pref)
        .addGap(HORIZONTAL_GAP)
        .addComponent(typeLabel, typeLabelWidth, typeLabelWidth, typeLabelWidth)
        .addGap(HORIZONTAL_GAP)
        .addComponent(contentLabel, def, def, max)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(Alignment.LEADING, false)
        .addComponent(timeLabel, pref, pref, pref)
        .addGap(VERTICAL_GAP)
        .addComponent(typeLabel, pref, pref, pref)
        .addGap(VERTICAL_GAP)
        .addComponent(contentLabel, pref, pref, max)
        .addGap(VERTICAL_GAP)
    );
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
