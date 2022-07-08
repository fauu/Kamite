package io.github.kamitejp.recognition;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Stream;

public record PostprocessedChunk(
  String content, String originalContent, List<String> labels, int score
) {
  public PostprocessedChunk(String content, String originalContent, String label, int score) {
    this(content, originalContent, Stream.of(label).collect(toList()), score);
  }
}
