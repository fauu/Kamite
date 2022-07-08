package io.github.kamitejp.geometry;

public record Point(int x, int y) {
  public java.awt.Point toAWT() {
    return new java.awt.Point(x, y);
  }

  public static Point fromAWT(java.awt.Point p) {
    return new Point(p.x, p.y);
  }
}
