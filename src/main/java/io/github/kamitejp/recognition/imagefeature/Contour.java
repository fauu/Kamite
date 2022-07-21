package io.github.kamitejp.recognition.imagefeature;

import java.util.List;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;

public class Contour {
  public List<Point> points;
  public int id;
  public int parent = 0;
  public Type type;

  public enum Type {
    HOLE,
    OUTER
  }

  public Contour() {
    this.type = Type.OUTER;
  }

  public Contour(List<Point> points, int id, Type type) {
    this.points = points;
    this.id = id;
    this.type = type;
  }

  public Rectangle getBoundingBox() {
    return getBoundingBox(null, null);
  }

  public Rectangle getBoundingBox(Integer leftCutoff, Integer rightCutoff) {
    var xmin = Integer.MAX_VALUE;
    var ymin = Integer.MAX_VALUE;
    int xmax = 0;
    int ymax = 0;
    for (var p : points) {
      if (leftCutoff != null && p.x() < leftCutoff)   continue; // NOPMD
      if (rightCutoff != null && p.x() > rightCutoff) continue; // NOPMD

      if (p.x() < xmin) xmin = p.x(); // NOPMD
      if (p.x() > xmax) xmax = p.x(); // NOPMD
      if (p.y() < ymin) ymin = p.y(); // NOPMD
      if (p.y() > ymax) ymax = p.y(); // NOPMD
    }
    return Rectangle.ofEdges(xmin, ymin, xmax, ymax);
  }
}
