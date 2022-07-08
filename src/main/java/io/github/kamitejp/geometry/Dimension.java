package io.github.kamitejp.geometry;

public record Dimension(int width, int height) {
  public static Dimension fromAWT(java.awt.Dimension d) {
    return new Dimension((int) d.getWidth(), (int) d.getHeight());
  }
}
