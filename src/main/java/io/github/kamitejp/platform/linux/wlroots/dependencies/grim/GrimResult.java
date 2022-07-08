package io.github.kamitejp.platform.linux.wlroots.dependencies.grim;

public sealed interface GrimResult
  permits GrimResult.ExecutionFailed,
          GrimResult.Error,
          GrimResult.Screenshot {
  record ExecutionFailed() implements GrimResult {}
  record Error(String error) implements GrimResult {}
  record Screenshot(java.awt.image.BufferedImage screenshot) implements GrimResult {}
}
