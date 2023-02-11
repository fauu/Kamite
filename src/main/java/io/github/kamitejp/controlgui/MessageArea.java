package io.github.kamitejp.controlgui;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class MessageArea extends JPanel {
  private JScrollPane scrollPane;

  public MessageArea() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setPreferredSize(getPreferredSize());
    setBackground(new Color(0x484542)); // XXX: Constant in ControlGUI
    setBorder(new EmptyBorder(6, 6, 6, 6)); // XXX

    scrollPane =  new JScrollPane(this);
    scrollPane.setBorder(
      BorderFactory.createLineBorder(UIManager.getDefaults().getColor("Table.background"))
    );
    scrollPane.setHorizontalScrollBarPolicy(
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
  }

  public JScrollPane getScrollPane() {
    return scrollPane;
  }
}
