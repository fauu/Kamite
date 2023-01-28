package io.github.kamitejp.geometry;

public record Point(int x, int y) {
  public double distanceFrom(Point other) {
    return Math.sqrt(
      Math.pow(x() - other.x(), 2)
      + Math.pow(y() - other.y(), 2)
    );
  }

  public double directedDistanceFromLine(Point lineA, Point lineB) {
    var a = x - lineA.x;
    var b = y - lineA.y;
    var c = lineB.x - lineA.x;
    var d = lineB.y - lineA.y;
    var e = -d;
    var f = c;

    var dot = a * e + b * f;
    var lenSq = e * e + f * f;

    return dot / Math.sqrt(lenSq);
  }

  public double angleWith(Point other) {
    return Math.atan2(other.y - y, other.x - x);
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
