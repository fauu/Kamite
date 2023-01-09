package io.github.kamitejp.chunk;

public record IncomingChunkText(String text, Double playbackTimeS) {
  public static IncomingChunkText of(String text) {
    return new IncomingChunkText(text, null);
  }
}
