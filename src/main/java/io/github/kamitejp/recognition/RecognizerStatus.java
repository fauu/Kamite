package io.github.kamitejp.recognition;

import java.util.List;

public class RecognizerStatus {
  private Kind kind;
  private List<String> availableCommands;
  private List<OCRConfigurationInfo> configurations;

  public RecognizerStatus(
    Kind kind,
    List<String> availableCommands,
    List<OCRConfigurationInfo> configurations
  ) {
    this.kind = kind;
    this.availableCommands = availableCommands;
    this.configurations = configurations;
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

  public List<OCRConfigurationInfo> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<OCRConfigurationInfo> configurations) {
    this.configurations = configurations;
  }

  public enum Kind {
    UNAVAILABLE,
    INITIALIZING,
    IDLE,
    AWAITING_USER_INPUT,
    PROCESSING,
  }
}
