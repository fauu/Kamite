package io.github.kamitejp.geometry;

public record Point(int x, int y) {
  public double distanceFrom(Point other) {
    return Math.sqrt(
      Math.pow(x() - other.x(), 2)
      + Math.pow(y() - other.y(), 2)
    );
  }

  public java.awt.Point toAWT() {
    return new java.awt.Point(x, y);
  }

  public static Point from(Point p) {
    return new Point(p.x(), p.y());
  }

  public static Point from(java.awt.Point p) {
    return new Point(p.x, p.y);
  }
}
