package io.github.kamitejp.recognition;

import io.github.kamitejp.config.Config;

public enum OCREngine {
  TESSERACT("tesseract"),
  MANGAOCR("mangaocr"),
  NONE("none");

  private String displayName;

  OCREngine(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static OCREngine fromConfigOCREngine(Config.OCREngine engine) {
    return switch (engine) {
      case MANGAOCR -> OCREngine.MANGAOCR;
      case TESSERACT -> OCREngine.TESSERACT;
      case NONE -> OCREngine.NONE;
    };
  }
}
