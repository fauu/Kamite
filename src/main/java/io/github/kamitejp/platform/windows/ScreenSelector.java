package io.github.kamitejp.platform.windows;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.Kamite;
import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;

public class ScreenSelector extends JFrame {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final List<Integer> CANCEL_KEYS = List.of(KeyEvent.VK_ESCAPE, KeyEvent.VK_Q);
  private static final Color FRAME_BG_COLOR = new Color(1.0f, 1.0f, 1.0f, 0.0f);
  private static final Color BG_FADE_COLOR = new Color(1.0f, 1.0f, 1.0f, 0.2f);
  private static final Stroke SELECTION_BORDER_STROKE = new BasicStroke(2);
  private static final Color SELECTION_BORDER_COLOR = Color.BLACK;

  // Selection start and end point coordinates. Cleared to 0
  private int xStart;
  private int yStart;
  private int xEnd;
  private int yEnd;

  private java.awt.Rectangle screenBounds;
  private CompletableFuture<Optional<Point>> futurePoint;
  private CompletableFuture<Optional<Rectangle>> futureArea;

  public ScreenSelector() {
    setTitle("%s OCR area selector".formatted(Kamite.APP_NAME_DISPLAY));
    setAlwaysOnTop(true);
    setUndecorated(true);
    setBackground(FRAME_BG_COLOR);
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    // POLISH: Should have its own modified icon
    setIconImage(
      Toolkit.getDefaultToolkit().getImage(getClass().getResource(ControlGUI.ICON_RESOURCE_PATH))
    );

    var fullBounds = new java.awt.Rectangle();
    for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      fullBounds = fullBounds.union(device.getDefaultConfiguration().getBounds());
    }
    setBounds(fullBounds);
    screenBounds = fullBounds;

    var mouseListener = new MouseListener();
    addMouseListener(mouseListener);
    addMouseMotionListener(mouseListener);

    var keyListener = new KeyListener();
    addKeyListener(keyListener);
  }

  @Override
  public void paint(Graphics gfx) {
    super.paint(gfx);
    var gfx2d = (Graphics2D) gfx;
    var oldStroke = gfx2d.getStroke();

    gfx2d.setColor(BG_FADE_COLOR);

    if (futurePoint != null) {
      // Selecting a point. No selection rectangle to draw, fade the entire frame
      gfx2d.fillRect(0, 0, getWidth(), getHeight());
      return;
    }

    // Selecting an area

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
    gfx2d.setStroke(SELECTION_BORDER_STROKE);
    gfx2d.setColor(SELECTION_BORDER_COLOR);
    gfx2d.drawRect(left, top, w, h);
    gfx2d.setStroke(oldStroke);
  }

  public CompletableFuture<Optional<Point>> getFuturePoint() {
    futurePoint = new CompletableFuture<>();
    activate();
    return futurePoint;
  }

  public CompletableFuture<Optional<Rectangle>> getFutureArea() {
    futureArea = new CompletableFuture<>();
    activate();
    return futureArea;
  }

  private void cancel() {
    if (futurePoint != null) {
      futurePoint.complete(Optional.empty());
    } else if (futureArea != null) {
      futureArea.complete(Optional.empty());
    }
    deactivate();
    reset();
  }

  private void activate() {
    setVisible(true);
    setExtendedState(JFrame.NORMAL);
    toFront();
    requestFocus();
  }

  private void deactivate() {
    setVisible(false);
  }

  private void reset() {
    xStart = 0;
    yStart = 0;
    xEnd = 0;
    yEnd = 0;
    futureArea = null;
    futurePoint = null;
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
      if (futurePoint != null) {
        futurePoint.complete(Optional.of(new Point(
          e.getX() + (int) screenBounds.getX(),
          e.getY() + (int) screenBounds.getY()
        )));
        deactivate();
        reset();
        return;
      }
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
      futureArea.complete(Optional.of(Rectangle.ofStartAndDimensions(x, y, w, h)));
      deactivate();
      reset();
    }
  }

  private class KeyListener implements java.awt.event.KeyListener {
    @Override
    public void keyTyped(KeyEvent e) {
      // Empty
    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (CANCEL_KEYS.contains(e.getKeyCode())) {
        cancel();
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
      // Empty
    }
  }
}
