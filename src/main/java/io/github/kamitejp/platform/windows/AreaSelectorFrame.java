package io.github.kamitejp.platform.windows;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.geometry.Rectangle;

public class AreaSelectorFrame extends JFrame {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private java.awt.Rectangle screenBounds;

  // Selection start and end point coordinates. Cleared to 0
  private int xStart;
  private int yStart;
  private int xEnd;
  private int yEnd;

  private CompletableFuture<Rectangle> futureArea;

  private final Color frameBgColor = new Color(1.0f, 1.0f, 1.0f, 0.0f);
  private final Color bgFadeColor = new Color(1.0f, 1.0f, 1.0f, 0.2f);
  private final Stroke selectionBorderStroke = new BasicStroke(2);
  private final Color selectionBorderColor = Color.BLACK;

  public AreaSelectorFrame() {
    setAlwaysOnTop(true);
    setUndecorated(true);
    setBackground(frameBgColor);
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    // POLISH: Should have its own modified icon
    setIconImage(
      Toolkit.getDefaultToolkit().getImage(getClass().getResource(ControlGUI.ICON_RESOURCE_PATH))
    );

    var fullBounds = new java.awt.Rectangle();
    for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      for (var conf : device.getConfigurations()) {
        fullBounds = fullBounds.union(conf.getBounds());
      }
    }
    setBounds(fullBounds);
    screenBounds = fullBounds;

    var mouseListener = new MouseListener();

    addMouseListener(mouseListener);
    addMouseMotionListener(mouseListener);
  }

  @Override
  public void paint(Graphics gfx) {
    super.paint(gfx);
    var gfx2d = (Graphics2D) gfx;
    var oldStroke = gfx2d.getStroke();

    gfx2d.setColor(bgFadeColor);

    // Selecton area rectangle dimensions
    var w = Math.abs(xStart - xEnd);
    var h = Math.abs(yStart - yEnd);
    if (w == 0 || h == 0) {
      // No selection rectangle to draw, fade the entire frame
      gfx2d.fillRect(0, 0, getWidth(), getHeight());
      return;
    }

    // Selecton area rectangle edges
    var top = Math.min(yStart, yEnd);
    var bottom = Math.max(yStart, yEnd);
    var left = Math.min(xStart, xEnd);
    var right = Math.max(xStart, xEnd);

    // Draw background fades around the rectangle
    // Top
    gfx2d.fillRect(0, 0, getWidth(), top);
    // Right
    gfx2d.fillRect(right, top, getWidth() - right, h);
    // Bottom
    gfx2d.fillRect(0, bottom, getWidth(), getHeight() - bottom);
    // Left
    gfx2d.fillRect(0, top, left, h);

    // Draw the selection border around the rectangle
    gfx2d.setStroke(selectionBorderStroke);
    gfx2d.setColor(selectionBorderColor);
    gfx2d.drawRect(left, top, w, h);
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

      if (futureArea == null) {
        return;
      }

      var x = Math.min(xStart, xEnd) + (int) screenBounds.getX();
      var y = Math.min(yStart, yEnd) + (int) screenBounds.getY();
      var w = Math.abs(xStart - xEnd);
      var h = Math.abs(yStart - yEnd);
      if (w <= 0 || h <= 0) {
        return;
      }
      futureArea.complete(Rectangle.ofStartAndDimensions(x, y, w, h));
      reset();
    }
  }
}
