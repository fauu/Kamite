package io.github.kamitejp.server.outmessage;

import java.util.List;

import io.github.kamitejp.chunk.ProcessedChunk;

public class ChunkVariantsOutMessage extends BaseOutMessage {
  private final List<ProcessedChunk> variants;
  private final Double playbackTimeS;

  public ChunkVariantsOutMessage(List<ProcessedChunk> variants, Double playbackTimeS) {
    super("chunk-variants");
    this.variants = variants;
    this.playbackTimeS = playbackTimeS;
  }

  public ChunkVariantsOutMessage(List<ProcessedChunk> variants) {
    this(variants, null);
  }

  public List<ProcessedChunk> getVariants() {
    return this.variants;
  }

  public Double getPlaybackTimeS() {
    return this.playbackTimeS;
  }
}
