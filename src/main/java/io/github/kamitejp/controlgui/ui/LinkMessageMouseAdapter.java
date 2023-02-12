package io.github.kamitejp.controlgui.ui;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class LinkMessageMouseAdapter extends MouseAdapter {
  private Consumer<Void> clickedCb;

  public LinkMessageMouseAdapter(Consumer<Void> clickedCb) {
    this.clickedCb = clickedCb;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    clickedCb.accept(null);
  }

  @Override
  public void mouseExited(MouseEvent e) {
    e.getComponent().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    e.getComponent().setCursor(new Cursor(Cursor.HAND_CURSOR));
  }
}
