package io.github.kamitejp.recognition;

import io.github.kamitejp.recognition.configuration.OcrConfigurationRecord;

import java.awt.image.BufferedImage;
import java.util.List;

public sealed interface RecognizerEvent
  permits RecognizerEvent.Initialized,
          RecognizerEvent.AvailableCommandsChanged,
          RecognizerEvent.OcrConfigurationRecordsUpdated,
          RecognizerEvent.DebugImageSubmitted {
  record Initialized() implements RecognizerEvent {}

  record AvailableCommandsChanged(List<String> availableCommands) implements RecognizerEvent {}

  record OcrConfigurationRecordsUpdated(List<OcrConfigurationRecord> records)
    implements RecognizerEvent {}

  record DebugImageSubmitted(BufferedImage image) implements RecognizerEvent {}
}
