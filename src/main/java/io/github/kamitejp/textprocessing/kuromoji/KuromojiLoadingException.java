package io.github.kamitejp.textprocessing.kuromoji;

public class KuromojiLoadingException extends Exception {
  KuromojiLoadingException(String message) {
    super(message);
  }

  KuromojiLoadingException(Throwable cause) {
    super(cause);
  }

  KuromojiLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
