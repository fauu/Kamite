package io.github.kamitejp.platform.windows;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFrame;

import io.github.kamitejp.geometry.Rectangle;

public class AreaSelectorFrame extends JFrame {
  private java.awt.Rectangle screenBounds;
  private int xStart;
  private int yStart;
  private int xEnd;
  private int yEnd;
  private CompletableFuture<Rectangle> futureArea;

  private final Color bgColor = new Color(1.0f, 1.0f, 1.0f, 0.2f);
  private final Stroke stroke = new BasicStroke(2);
  private final Color color = Color.BLACK;

  public AreaSelectorFrame() {
    setAlwaysOnTop(true);
    setUndecorated(true);
    setBackground(Color.WHITE);
    setOpacity(0.3f);

    var fullBounds = new java.awt.Rectangle();
    for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) { 
      for (var conf : device.getConfigurations()) {
        fullBounds = fullBounds.union(conf.getBounds());
      }
    }
    setBounds(fullBounds);
    this.screenBounds = fullBounds;

    var mouseListener = new MouseListener();

    addMouseListener(mouseListener);
    addMouseMotionListener(mouseListener);
  }

  @Override
  public void paint(Graphics gfx) {
    super.paint(gfx);
    var gfx2d = (Graphics2D) gfx;
    var oldStroke = gfx2d.getStroke();

    var w = Math.abs(xStart - xEnd);
    var h = Math.abs(yStart - yEnd);
    var top = Math.min(yStart, yEnd);
    var bottom = Math.max(yStart, yEnd);
    var left = Math.min(xStart, xEnd);
    var right = Math.max(xStart, xEnd);
    if (w == 0 || h == 0) {
      gfx2d.setColor(bgColor);
      gfx2d.fillRect(0, 0, getWidth(), getHeight());
    } else {
      gfx2d.setColor(bgColor);
      // Top background
      gfx2d.fillRect(0, 0, getWidth(), top);
      // Right background
      gfx2d.fillRect(right, top, getWidth() - right, h);
      // Bottom background
      gfx2d.fillRect(0, bottom, getWidth(), getHeight() - bottom);
      // Left background
      gfx2d.fillRect(0, top, left, h);

      gfx2d.setColor(color);
      gfx2d.setStroke(stroke);
      gfx2d.drawRect(left, top, w, h);
    }
    gfx2d.setStroke(oldStroke);
  }

  public CompletableFuture<Rectangle> getRegion() {
    futureArea = new CompletableFuture<>();
    return futureArea;
  }

  private void reset() {
    xStart = 0;
    yStart = 0;
    xEnd = 0;
    yEnd = 0;
  }

  private void setStartPoint(int x, int y) {
    xStart = x;
    yStart = y;
  }

  private void setEndPoint(int x, int y) {
    xEnd = x;
    yEnd = y;
  }

  private class MouseListener extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      setStartPoint(e.getX(), e.getY());
    }

    public void mouseDragged(MouseEvent e) {
      setEndPoint(e.getX(), e.getY());
      repaint();
    }

    public void mouseReleased(MouseEvent e) {
      setEndPoint(e.getX(), e.getY());
      repaint();

      if (AreaSelectorFrame.this.futureArea == null) {
        return;
      }

      var x = Math.min(xStart, xEnd) + (int) AreaSelectorFrame.this.screenBounds.getX();
      var y = Math.min(yStart, yEnd) + (int) AreaSelectorFrame.this.screenBounds.getY();
      var w = Math.abs(xStart - xEnd);
      var h = Math.abs(yStart - yEnd);
      if (w <= 0 || h <= 0) {
        return;
      }
      AreaSelectorFrame.this.futureArea.complete(Rectangle.ofStartAndDimensions(x, y, w, h));
      AreaSelectorFrame.this.reset();
    }
  }
}
