package io.github.kamitejp.recognition;

import java.util.List;

public class RecognizerStatus {
  private Kind kind;
  private List<String> availableCommands;

  public RecognizerStatus(Kind kind, List<String> availableCommands) {
    this.kind = kind;
    this.availableCommands = availableCommands;
  }

  public Kind getKind() {
    return kind;
  }

  public void setKind(Kind kind) {
    this.kind = kind;
  }

  public List<String> getAvailableCommands() {
    return availableCommands;
  }

  public void setAvailableCommands(List<String> availableCommands) {
    this.availableCommands = availableCommands;
  }

  public enum Kind {
    UNAVAILABLE,
    INITIALIZING,
    IDLE,
    AWAITING_USER_INPUT,
    PROCESSING,
  }
}
