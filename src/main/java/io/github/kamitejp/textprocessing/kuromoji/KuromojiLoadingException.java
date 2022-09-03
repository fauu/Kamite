package io.github.kamitejp.textprocessing.kuromoji;

public class KuromojiLoadingException extends Exception {
  public KuromojiLoadingException(String message) {
    super(message);
  }

  public KuromojiLoadingException(Throwable cause) {
    super(cause);
  }

  public KuromojiLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
