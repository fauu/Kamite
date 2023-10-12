package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.chunk.ChunkTranslationDestination;

public class ChunkTranslationOutMessage extends BaseOutMessage {
  private final String translation;
  private final ChunkTranslationDestination destination;
  private final Double playbackTimeS;

  public ChunkTranslationOutMessage(
    String translation,
    ChunkTranslationDestination destination,
    Double playbackTimeS
  ) {
    super("chunk-translation");
    this.translation = translation;
    this.destination = destination;
    this.playbackTimeS = playbackTimeS;
  }

  public String getTranslation() {
    return this.translation;
  }

  public ChunkTranslationDestination getDestination() {
    return this.destination;
  }

  public Double getPlaybackTimeS() {
    return this.playbackTimeS;
  }
}
