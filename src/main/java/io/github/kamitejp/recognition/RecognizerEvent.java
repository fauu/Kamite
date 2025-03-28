package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.util.List;

public sealed interface RecognizerEvent
  permits RecognizerEvent.Initialized,
          RecognizerEvent.OCRConfigurationListUpdated,
          RecognizerEvent.DebugImageSubmitted {
  record Initialized(List<String> availableCommands) implements RecognizerEvent {}

  record OCRConfigurationListUpdated(List<OCRConfigurationInfo> configurations)
    implements RecognizerEvent {}

  record DebugImageSubmitted(BufferedImage image) implements RecognizerEvent {}
}
