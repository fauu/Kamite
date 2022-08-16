package io.github.kamitejp.geometry;

import java.awt.Graphics;
import java.util.List;

public final class Rectangle {
  private int left;
  private int top;
  private int right;
  private int bottom;

  private Rectangle(int left, int top, int right, int bottom) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public int getWidth() {
    return right - left + 1;
  }

  public int getHeight() {
    return bottom - top + 1;
  }

  public int getArea() {
    return getWidth() * getHeight();
  }

  public float getRatio() {
    return (float) getWidth() / getHeight();
  }

  public boolean dimensionsWithin(int min, int max) {
    var w = getWidth();
    var h = getHeight();
    return w >= min && w <= max && h >= min && h <= max;
  }

  public boolean widthWithin(int min, int max) {
    return getWidth() >= min && getWidth() <= max;
  }

  public boolean heightWithin(int min, int max) {
    return getHeight() >= min && getHeight() <= max;
  }

  public Rectangle clamped(int rightMax, int bottomMax) {
    var nleft   =  left   > rightMax  ? rightMax  : left;
    var nright  =  right  > rightMax  ? rightMax  : right;
    var ntop    =  top    > bottomMax ? bottomMax : top;
    var nbottom =  bottom > bottomMax ? bottomMax : bottom;
    return nleft != left || nright != right || ntop != top || nbottom != bottom
      ? new Rectangle(nleft, ntop, nright, nbottom)
      : this;
  }

  public Rectangle expandedNonNegative(int amount) {
    var nleft = left - amount;
    if (nleft < 0) {
      nleft = 0;
    }
    var nright = right + amount;

    var ntop = top - amount;
    if (ntop < 0) {
      ntop = 0;
    }
    var nbottom = bottom + amount;

    return new Rectangle(nleft, ntop, nright, nbottom);
  }

  public Rectangle shifted(int shift) {
    return shifted(shift, shift);
  }

  public Rectangle shifted(int xShift, int yShift) {
    return new Rectangle(left + xShift, top + yShift, right + xShift, bottom + yShift);
  }

  public Point getCenter() {
    return new Point((int) (left + (getWidth() / 2)), (int) (top + (getHeight() / 2)));
  }

  public boolean contains(Point p) {
    return getLeft() <= p.x() && p.x() <= getRight() && getTop() <= p.y() && p.y() <= getBottom();
  }

  public java.awt.Rectangle toAWT() {
    return new java.awt.Rectangle(left, top, getWidth(), getHeight());
  }

  public static Rectangle around(Point p, Dimension dimension) {
    return around(p, dimension.width(), dimension.height());
  }

  public static Rectangle around(Point p, int dimension) {
    return around(p, dimension, dimension);
  }

  public static Rectangle around(Point p, int width, int height) {
    var halfW = width / 2;
    var halfH = height / 2;
    return new Rectangle(p.x() - halfW, p.y() - halfH, p.x() + halfW, p.y() + halfH);
  }

  public static Rectangle around(List<Rectangle> rects) {
    var top = Integer.MAX_VALUE;
    var bottom = Integer.MIN_VALUE;
    var left = Integer.MAX_VALUE;
    var right = Integer.MIN_VALUE;
    for (var r : rects) {
      if (r.getLeft() < left) {
        left = r.getLeft();
      }
      if (r.getTop() < top) {
        top = r.getTop();
      }
      if (r.getRight() > right) {
        right = r.getRight();
      }
      if (r.getBottom() > bottom) {
        bottom = r.getBottom();
      }
    }
    return Rectangle.ofEdges(left, top, right, bottom);
  }

  public static Rectangle ofEdges(int left, int top, int right, int bottom) {
    if (left < 0 || top < 0 || right < left || bottom < top) {
      throw new IllegalArgumentException("Incorrect edges provided for a rectangle");
    }
    return new Rectangle(left, top, right, bottom);
  }

  public static Rectangle ofStartAndDimensions(int x, int y, int width, int height) {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("Incorrect dimensions provided for a rectangle");
    }
    return new Rectangle(x, y, x + width, y + height);
  }

  public int getLeft() {
    return left;
  }

  public int getTop() {
    return top;
  }

  public int getRight() {
    return right;
  }

  public int getBottom() {
    return bottom;
  }

  public void drawWith(Graphics gfx) {
    gfx.drawRect(getLeft(), getTop(), getWidth(), getHeight());
  }

  public void drawFilledWith(Graphics gfx) {
    gfx.fillRect(getLeft(), getTop(), getWidth(), getHeight());
  }

  public static <T extends java.awt.geom.Rectangle2D> Rectangle fromAWT(T r) {
    return Rectangle.ofStartAndDimensions(
      (int) r.getX(),
      (int) r.getY(),
      (int) r.getWidth(),
      (int) r.getHeight()
    );
  }

  @Override
  public String toString() {
    return "Rectangle[left=%s,top=%s,right=%s,bottom=%s]".formatted(left, top, right, bottom);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Rectangle other) {
      return left == other.left
        && top == other.top
        && right == other.right
        && bottom == other.bottom;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 31 * (31 * (31 * left + top) + right) + bottom;
  }
}
