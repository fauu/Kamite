package io.github.kamitejp.controlgui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class MessageArea extends JPanel {
  private JScrollPane scrollPane;

  public MessageArea() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setPreferredSize(getPreferredSize());
    setBackground(ControlGUI.COLOR_BG2);
    setBorder(ControlGUI.MESSAGE_AREA_BORDER);

    scrollPane =  new JScrollPane(this);
    scrollPane.setBorder(BorderFactory.createLineBorder(ControlGUI.COLOR_BG2_HL));
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
  }

  public JScrollPane getScrollPane() {
    return scrollPane;
  }
}
