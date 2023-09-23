package io.github.kamitejp.textprocessing.jumanpp;

public class JumanppLoadingException extends Exception {
  JumanppLoadingException(String message) {
    super(message);
  }

  JumanppLoadingException(Throwable cause) {
    super(cause);
  }

  JumanppLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
