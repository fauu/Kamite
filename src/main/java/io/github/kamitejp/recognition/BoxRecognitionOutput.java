package io.github.kamitejp.recognition;

import io.github.kamitejp.chunk.UnprocessedChunkVariants;

public record BoxRecognitionOutput(UnprocessedChunkVariants chunkVariants) {
  public boolean isEmpty() {
    return chunkVariants.isEmpty();
  }

  public static BoxRecognitionOutput fromString(String s) {
    return new BoxRecognitionOutput(UnprocessedChunkVariants.singleFromString(s));
  }
}
