package io.github.kamitejp.controlgui.ui;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import io.github.kamitejp.controlgui.ControlGUI;

public class MessageArea extends JPanel {
  private static final int MAX_MESSAGES = 100;

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

  @Override
  public Component add(Component comp) {
    if (getComponentCount() >= MAX_MESSAGES) {
      remove(0);
    }
    return super.add(comp);
  }

  public JScrollPane getScrollPane() {
    return scrollPane;
  }
}
