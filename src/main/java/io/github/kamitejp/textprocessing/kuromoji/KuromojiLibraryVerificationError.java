package io.github.kamitejp.textprocessing.kuromoji;

public enum KuromojiLibraryVerificationError {
  COULD_NOT_DETERMINE_PATH,
  NO_READABLE_FILE_AT_PATH,
  COULD_NOT_COMPUTE_HASH,
  HASH_DOES_NOT_MATCH
}
