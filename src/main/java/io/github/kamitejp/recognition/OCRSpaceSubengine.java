package io.github.kamitejp.recognition;

public enum OCRSpaceSubengine {
  ENGINE_1,
  ENGINE_3;

  public static OCRSpaceSubengine fromNumber(int no) {
    if (no == 1) {
      return ENGINE_1;
    } else if (no == 3) {
      return ENGINE_3;
    }
    throw new IllegalArgumentException(
      "Could not construct OCRSpaceSubengine from engine number %d".formatted(no)
    );
  }

  public int toNumber() {
    if (this == ENGINE_1) {
      return 1;
    } else if (this == ENGINE_3) {
      return 3;
    }
    throw new IllegalStateException("OCRSpaceSubengine enum is incomplete");
  }
}
