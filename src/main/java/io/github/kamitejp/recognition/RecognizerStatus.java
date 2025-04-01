package io.github.kamitejp.recognition;

import io.github.kamitejp.recognition.configuration.OcrConfigurationRecord;

import java.util.List;

public class RecognizerStatus {
  private Kind kind;
  private List<String> availableCommands;
  private List<OcrConfigurationRecord> ocrConfigurations;

  public RecognizerStatus(
    Kind kind,
    List<String> availableCommands,
    List<OcrConfigurationRecord> ocrConfigurations
  ) {
    this.kind = kind;
    this.availableCommands = availableCommands;
    this.ocrConfigurations = ocrConfigurations;
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

  public List<OcrConfigurationRecord> getOcrConfigurations() {
    return ocrConfigurations;
  }

  public void setOcrConfigurations(List<OcrConfigurationRecord> configurations) {
    this.ocrConfigurations = configurations;
  }

  public enum Kind {
    UNAVAILABLE,
    INITIALIZING,
    IDLE,
    AWAITING_USER_INPUT,
    PROCESSING,
  }
}
