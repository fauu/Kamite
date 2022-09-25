package io.github.kamitejp.recognition;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.kamitejp.chunk.ChunkTransformer;
import io.github.kamitejp.recognition.Recognizer.LabelledTesseractHOCROutput;

public final class ChunkVariants {
  private static final int CHUNK_SCORE_BONUS_PER_DUPLICATE = 5;

  private List<Chunk> variants;

  private ChunkVariants() {}

  private ChunkVariants(List<Chunk> variants) {
    this.variants = variants;
  }

  public static ChunkVariants singleFromString(String s) {
    return new ChunkVariants(Stream.of(new Chunk(s, "single", 150)).collect(toList()));
  }

  public static ChunkVariants fromLabelledTesseractHOCROutputs(
      List<LabelledTesseractHOCROutput> outputs) {
    return new ChunkVariants(
      outputs.stream()
        .map(o -> Chunk.fromHOCRWithLabel(o.hocr(), o.label()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toList())
    );
  }

  public void add(int idx, String content, String label, int score) {
    this.variants.add(idx, new Chunk(content, label, score));
  }

  public boolean isEmpty() {
    return this.variants.isEmpty();
  }

  public void sortByScore() {
    this.variants.sort(comparing(Chunk::getScore).reversed());
  }

  public void deduplicate() {
    this.variants = this.variants.stream()
      .collect(groupingBy(Chunk::getContent))
      .entrySet()
      .stream()
      .map(dupEntry -> {
        var duplicates = dupEntry.getValue();
        // Set the score to the max score of the duplicates + bonus per duplicate
        var newScore = 0;
        var newLabels = new ArrayList<String>();
        for (var chunk : duplicates) {
          if (chunk.getScore() > newScore) {
            newScore = chunk.getScore();
          }
          newLabels.addAll(chunk.getLabels());
        }
        newScore += (duplicates.size() - 1) * CHUNK_SCORE_BONUS_PER_DUPLICATE;
        return new Chunk(/* content */ dupEntry.getKey(), newLabels, newScore);
      })
      .collect(toList());
  }

  public List<PostprocessedChunk> getPostprocessedChunks(
    boolean correct, ChunkTransformer transformer
  ) {
    var result = new ArrayList<PostprocessedChunk>();
    for (int i = 0; i < this.variants.size(); i++) {
      var variant = this.variants.get(i);
      var content = correct ? variant.getCorrectedContent() : variant.getContent();
      if (transformer != null) {
        content = transformer.execute(content);
        if (content.isBlank()) {
          continue;
        }
      }

      String finalContent;
      if (i == 0) {
        // The first variant gets automatically pulled as the main chunk and therefore doesn't
        // appear in the list of variants, so we don't need to mark it. If we did, it would also
        // mess with character counting
        finalContent = content;
      } else {
        finalContent = variantContentMarkGlobalUniqueCharacters(content, i);
      }

      String originalContent = variant.getContent();
      if (content.equals(originalContent)) {
        originalContent = null;
      }
      result.add(new PostprocessedChunk(
        finalContent,
        originalContent,
        variant.getLabels(),
        variant.getScore()
      ));
    }
    return result;
  }

  // Puts '@' before every character in a given variant that doesn't appear in any other variants.
  // TODO: Store this in a separate list of string indices instead of modifying the string itself
  private String variantContentMarkGlobalUniqueCharacters(String variantContent, int variantIdx) {
    var variantChars = variantContent.chars().mapToObj(ch -> (char)ch).collect(toList());
    for (int chIdx = 0; chIdx < variantChars.size(); chIdx++) {
      var ch = variantChars.get(chIdx);
      if (ch == '\n') {
        continue;
      }
      boolean found = false;
      for (int i = 0; i < this.variants.size(); i++) {
        if (i == variantIdx) {
          continue;
        }
        if (this.variants.get(i).getContent().contains(String.valueOf(ch))) {
          found = true;
          break;
        }
      }
      if (!found) {
        variantChars.add(chIdx++, '@');
      }
    }
    return variantChars.stream().map(Object::toString).collect(joining());
  }
  
  public List<Chunk> getVariants() {
    return this.variants;
  }
}
