package io.github.kamitejp.recognition.imagefeature;

import io.github.kamitejp.geometry.Rectangle;

public class ConnectedComponent {
  public int label;
  public int x1;
  public int y1;
  public int x2;
  public int y2;

  public ConnectedComponent(int label, int x1, int y1, int x2, int y2) {
    this.label = label;
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  public Rectangle rectangle() {
    return Rectangle.ofEdges(x1, y1, x2, y2);
  }
}
