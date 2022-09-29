package io.github.kamitejp.textprocessing;

class ProcessingToken {
  String surfaceForm;
  String reading;
  private final String partOfSpeech;

  ProcessingToken(String surfaceForm, String reading, String partOfSpeech) {
    this.surfaceForm = surfaceForm;
    this.reading = reading;
    this.partOfSpeech = partOfSpeech;
  }

  boolean isPartOfSpeechNonEmptyEqual(String pos) {
    return partOfSpeech != null && !partOfSpeech.isEmpty() && pos.equals(partOfSpeech); // NOPMD - we need to check for empty before checking for equals, and for that we need to check for null early
  }

  @Override
  public String toString() {
    return "ProcessingToken[%s; %s; %s]".formatted(surfaceForm, reading, partOfSpeech);
  }
}
