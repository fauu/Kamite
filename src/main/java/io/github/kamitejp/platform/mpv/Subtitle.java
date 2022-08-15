package io.github.kamitejp.platform.mpv;

public record Subtitle(Kind kind, String text, double startTimeS) {
  public enum Kind {
    PRIMARY,
    SECONDARY;
  }
}
