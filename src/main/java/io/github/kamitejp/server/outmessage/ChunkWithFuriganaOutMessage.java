package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.textprocessing.ChunkWithFurigana;

public class ChunkWithFuriganaOutMessage extends BaseOutMessage {
  private final ChunkWithFurigana chunkWithFurigana;

  public ChunkWithFuriganaOutMessage(ChunkWithFurigana chunkWithFurigana) {
    super("chunk-with-furigana");
    this.chunkWithFurigana = chunkWithFurigana;
  }

  public ChunkWithFurigana getChunkWithFurigana() {
    return this.chunkWithFurigana;
  }
}
