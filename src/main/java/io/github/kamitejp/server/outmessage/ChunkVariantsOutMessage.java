package io.github.kamitejp.server.outmessage;

import java.util.List;

import io.github.kamitejp.recognition.PostprocessedChunk;

public class ChunkVariantsOutMessage extends BaseOutMessage {
  private final List<PostprocessedChunk> variants;
  private final Double playbackTimeS;

  public ChunkVariantsOutMessage(List<PostprocessedChunk> variants, Double playbackTimeS) {
    super("chunk-variants");
    this.variants = variants;
    this.playbackTimeS = playbackTimeS;
  }

  public ChunkVariantsOutMessage(List<PostprocessedChunk> variants) {
    this(variants, null);
  }

  public List<PostprocessedChunk> getVariants() {
    return this.variants;
  }

  public Double getPlaybackTimeS() {
    return this.playbackTimeS;
  }
}
