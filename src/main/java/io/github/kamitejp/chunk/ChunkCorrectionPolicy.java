package io.github.kamitejp.chunk;

import io.github.kamitejp.config.Config;

public enum ChunkCorrectionPolicy {
  DO_CORRECT,
  DO_NOT_CORRECT;

  public static ChunkCorrectionPolicy fromChunkConfig(Config.Chunk chunkConfig) {
    return chunkConfig.correct() ? DO_CORRECT : DO_NOT_CORRECT;
  }
}
