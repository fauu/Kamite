package io.github.kamitejp.platform;

public class PlatformCreationException extends Exception {
  protected PlatformCreationException() {
    super();
  }

  public PlatformCreationException(String message) {
    super(message);
  }

  public PlatformCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}
