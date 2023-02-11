package io.github.kamitejp.controlgui.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

import io.github.kamitejp.controlgui.ControlGUI;

public class MessageArea extends JPanel implements Scrollable {
  private static final int MAX_MESSAGES = 100;

  private static final int SCROLLABLE_INCREMENT = 30;

  private JScrollPane scrollPane;

  public MessageArea() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBackground(ControlGUI.COLOR_BG2);
    setBorder(ControlGUI.MESSAGE_AREA_BORDER);

    scrollPane = new JScrollPane(this);
    scrollPane.setBorder(BorderFactory.createLineBorder(ControlGUI.COLOR_BG2_HL));
    // QUAL: This is a hack to ide the fact that MessageArea doesn't fill the height of the
    //       scrollPane
    scrollPane.getViewport().setBackground(getBackground());
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

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return SCROLLABLE_INCREMENT;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return SCROLLABLE_INCREMENT;
  }
}
