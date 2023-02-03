package io.github.kamitejp.chunk;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.kamitejp.recognition.Recognizer;

public final class UnprocessedChunkVariants {
  private static final int CHUNK_SCORE_BONUS_PER_DUPLICATE = 5;

  private List<Chunk> variants;

  private UnprocessedChunkVariants(List<Chunk> variants) {
    this.variants = variants;
  }

  public static UnprocessedChunkVariants singleFromString(String s) {
    return new UnprocessedChunkVariants(Stream.of(new Chunk(s, "single", 150)).collect(toList()));
  }

  public static UnprocessedChunkVariants fromLabelledTesseractHOCROutputs(
      List<Recognizer.LabelledTesseractHOCROutput> outputs) {
    return new UnprocessedChunkVariants(
      outputs.stream()
        .map(o -> Chunk.fromHOCRWithLabel(o.hocr(), o.label()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toList())
    );
  }

  public void add(int index, String content, String label, int score) {
    variants.add(index, new Chunk(content, label, score));
  }

  public boolean isEmpty() {
    return variants.isEmpty();
  }

  public void sortByScore() {
    variants.sort(comparing(Chunk::getScore).reversed());
  }

  public void deduplicate() {
    variants = variants.stream()
      .collect(groupingBy(Chunk::getContent))
      .entrySet()
      .stream()
      .map(dupEntry -> {
        var duplicates = dupEntry.getValue();
        // Set the score to the max score of the duplicates + bonus per duplicate
        var newScore = 0;
        var newLabels = new ArrayList<String>(8);
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

  public List<ProcessedChunk> process(
    ChunkCorrectionPolicy correctionPolicy, ChunkTransformer transformer
  ) {
    var processedChunks = new ArrayList<ProcessedChunk>(variants.size());

    var moreThanOneVariant = variants.size() > 1;

    for (int i = 0; i < variants.size(); i++) {
      var variant = variants.get(i);

      var processingContent =
        correctionPolicy == ChunkCorrectionPolicy.DO_CORRECT
        ? variant.getCorrectedContent()
        : variant.getContent();

      if (transformer != null) {
        processingContent = transformer.execute(processingContent);
      }

      variant.modifyContent(processingContent);

      // Reject blank text post-correction and post-transformation
      if (processingContent.isBlank()) {
        continue;
      }

      var enhancements =
        moreThanOneVariant
        ? ChunkEnhancements.ofInterVariantUniqueCharacterIndices(
            getInterVariantUniqueCharacterIndices(i)
          )
        : ChunkEnhancements.empty();

      processedChunks.add(ProcessedChunk.fromChunk(variant, enhancements));
    }

    return processedChunks;
  }

  // Marks characters in a given variant that don't appear in any other variant
  private List<Integer> getInterVariantUniqueCharacterIndices(int variantIdx) {
    var variantContent = variants.get(variantIdx).getContent();
    var indices = new ArrayList<Integer>(variantContent.length());
    var variantChars = variantContent.chars().mapToObj(ch -> (char) ch).collect(toList());
    for (int chIdx = 0; chIdx < variantChars.size(); chIdx++) {
      var ch = variantChars.get(chIdx);
      if (ch == '\n') {
        // Don't mark newlines
        continue;
      }
      var foundAnotherOccurrenceOfCh = false;
      for (var i = 0; i < variants.size(); i++) {
        if (i == variantIdx) {
          // Don't compare the current variant with itself
          continue;
        }
        if (variants.get(i).getContent().contains(String.valueOf(ch))) {
          foundAnotherOccurrenceOfCh = true;
          break;
        }
      }
      if (!foundAnotherOccurrenceOfCh) {
        indices.add(chIdx);
      }
    }
    return indices;
  }
}
