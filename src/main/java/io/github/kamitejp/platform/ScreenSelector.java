package io.github.kamitejp.platform;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.swing.JFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.Kamite;
import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;

public final class ScreenSelector extends JFrame {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final List<Integer> CANCEL_KEYS = List.of(KeyEvent.VK_ESCAPE, KeyEvent.VK_Q);
  private static final Color FRAME_BG_COLOR = new Color(1.0f, 1.0f, 1.0f, 0.0f);
  private static final Color BG_FADE_COLOR = new Color(1.0f, 1.0f, 1.0f, 0.2f);
  private static final Stroke SELECTION_BORDER_STROKE = new BasicStroke(2);
  private static final Color SELECTION_BORDER_COLOR = Color.BLACK;

  private Supplier<Point> customVirtualScreenCursorPositionSupplier;

  // Selection start and end point in frame coordinates (for drawing area rectangle)
  private Point frameStart;
  private Point frameEnd;

  // Selection start and end point in actual Windows virtual screen coordinates (for returning)
  private Point screenStart;
  private Point screenEnd;

  private CompletableFuture<Optional<Point>> futurePoint;
  private CompletableFuture<Optional<Rectangle>> futureArea;

  public ScreenSelector() {
    this(/* customVirtualScreenCursorPositionSupplier */ null);
  }

  // NOTE: Works on Xorg with a compositor if the secondary display is below the first. If it's
  //       above, however, the frame only covers the primary one.
  //       Without a compositor, we can't even draw a transparent window (JNA WindowUtils doesn't
  //       help). slop handles this without transparency by creating a custom-shaped window using
  //       the Xorg SHAPE extension.
  public ScreenSelector(Supplier<Point> customVirtualScreenCursorPositionSupplier) {
    this.customVirtualScreenCursorPositionSupplier = customVirtualScreenCursorPositionSupplier;

    setTitle("%s OCR area selector".formatted(Kamite.APP_NAME_DISPLAY));
    setAlwaysOnTop(true);
    setUndecorated(true);
    setBackground(FRAME_BG_COLOR);
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    // POLISH: Should have its own modified icon
    setIconImages(ControlGUI.ICON_IMAGES);

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
    int w = 0;
    int h = 0;
    if (frameStart != null && frameEnd != null) {
      w = Math.abs(frameStart.x() - frameEnd.x());
      h = Math.abs(frameStart.y() - frameEnd.y());
    }

    if (w == 0 || h == 0) {
      // No selection rectangle to draw, fade the entire frame
      gfx2d.fillRect(0, 0, getWidth(), getHeight());
      return;
    }

    // Selecton area rectangle edges
    var top = Math.min(frameStart.y(), frameEnd.y());
    var bottom = Math.max(frameStart.y(), frameEnd.y());
    var left = Math.min(frameStart.x(), frameEnd.x());
    var right = Math.max(frameStart.x(), frameEnd.x());

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
    if (futureArea != null) {
      cancel();
      futureArea = null;
    }
    futurePoint = new CompletableFuture<>();
    activate();
    return futurePoint;
  }

  public CompletableFuture<Optional<Rectangle>> getFutureArea() {
    if (futurePoint != null) {
      cancel();
      futurePoint = null;
    }
    futureArea = new CompletableFuture<>();
    activate();
    return futureArea;
  }

  private void cancel() {
    if (futurePoint != null) {
      futurePoint.complete(Optional.empty());
      futurePoint = null;
    } else if (futureArea != null) {
      futureArea.complete(Optional.empty());
      futureArea = null;
    }
    deactivate();
    reset();
  }

  private void activate() {
    var fullBounds = new java.awt.Rectangle();
    for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      for (var conf : device.getConfigurations()) {
        fullBounds = fullBounds.union(conf.getBounds());
      }
    }
    setBounds(fullBounds);

    setVisible(true);
    setExtendedState(NORMAL);
    toFront();
    requestFocus();
  }

  private void deactivate() {
    setVisible(false);
  }

  private void reset() {
    frameStart = null;
    frameEnd = null;
    screenStart = null;
    screenEnd = null;
  }

  private void setStartPoint(java.awt.Point frame, Point screen) {
    frameStart = Point.from(frame);
    screenStart = screen;
  }

  private void setEndPoint(java.awt.Point frame, Point screen) {
    frameEnd = Point.from(frame);
    screenEnd = screen;
  }

  private Point getVirtualScreenCursorPosition(MouseEvent e) {
    return customVirtualScreenCursorPositionSupplier != null
      ? customVirtualScreenCursorPositionSupplier.get()
      : Point.from(e.getLocationOnScreen());
  }

  private final class MouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (futurePoint != null) {
        deactivate();
        reset();
        futurePoint.complete(Optional.of(getVirtualScreenCursorPosition(e)));
        return;
      }
      setStartPoint(e.getPoint(), getVirtualScreenCursorPosition(e));
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      setEndPoint(e.getPoint(), getVirtualScreenCursorPosition(e));
      repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      setEndPoint(e.getPoint(), getVirtualScreenCursorPosition(e));
      repaint();

      if (futureArea == null) {
        return;
      }

      var x = Math.min(screenStart.x(), screenEnd.x());
      var y = Math.min(screenStart.y(), screenEnd.y());
      var w = Math.abs(screenStart.x() - screenEnd.x());
      var h = Math.abs(screenStart.y() - screenEnd.y());
      if (w <= 0 || h <= 0) {
        return;
      }
      deactivate();
      reset();
      futureArea.complete(Optional.of(Rectangle.ofStartAndDimensions(x, y, w, h)));
    }
  }

  private final class KeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      if (CANCEL_KEYS.contains(e.getKeyCode())) {
        cancel();
      }
    }
  }
}
