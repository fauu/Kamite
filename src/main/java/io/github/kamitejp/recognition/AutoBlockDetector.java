package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.kamitejp.geometry.Rectangle;

interface AutoBlockDetector {
  Optional<Rectangle> detect(
    BufferedImage img,
    boolean debug,
    BiConsumer<BufferedImage, String> sendDebugImage
  );

  static AutoBlockDetector fromHeuristic(AutoBlockHeuristic heuristic) {
    return switch (heuristic) {
      case MANGA_FULL   -> new MangaAutoBlockDetector();
      case GAME_TEXTBOX -> new GameTextboxAutoBlockDetector();
      default -> throw new UnsupportedOperationException(
        "Auto block detection for requested heuristic not implemented"
      );
    };
  }
}
