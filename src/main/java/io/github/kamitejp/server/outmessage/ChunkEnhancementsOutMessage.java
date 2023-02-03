package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.chunk.ChunkEnhancements;

public class ChunkEnhancementsOutMessage extends BaseOutMessage {
  private final ChunkEnhancements chunkEnhancements;

  public ChunkEnhancementsOutMessage(ChunkEnhancements chunkEnhancements) {
    super("chunk-enhancements");
    this.chunkEnhancements = chunkEnhancements;
  }

  public ChunkEnhancements getChunkEnhancements() {
    return chunkEnhancements;
  }
}
