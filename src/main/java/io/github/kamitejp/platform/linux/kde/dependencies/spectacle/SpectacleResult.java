package io.github.kamitejp.platform.linux.kde.dependencies.spectacle;

public sealed interface SpectacleResult
        permits SpectacleResult.ExecutionFailed,
        SpectacleResult.Error,
        SpectacleResult.Screenshot {
  record ExecutionFailed() implements SpectacleResult {}
  record Error(String error) implements SpectacleResult {}
  record Screenshot(java.awt.image.BufferedImage screenshot) implements SpectacleResult {}
}
