package io.github.kamitejp.server.outmessage;

public class ChunkTranslationOutMessage extends BaseOutMessage {
  private final String translation;
  private final Double playbackTimeS;

  public ChunkTranslationOutMessage(String translation, Double playbackTimeS) {
    super("chunk-translation");
    this.translation = translation;
    this.playbackTimeS = playbackTimeS;
  }

  public String getTranslation() {
    return this.translation;
  }

  public Double getPlaybackTimeS() {
    return this.playbackTimeS;
  }
}
