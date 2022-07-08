package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.util.List;

public sealed interface RecognizerEvent
  permits RecognizerEvent.Initialized,
          RecognizerEvent.MangaOCRStartedDownloadingModel,
          RecognizerEvent.Crashed,
          RecognizerEvent.Restarting,
          RecognizerEvent.DebugImageSubmitted {
  record Initialized(List<String> availableCommands) implements RecognizerEvent {}
  record MangaOCRStartedDownloadingModel() implements RecognizerEvent {}
  record Crashed() implements RecognizerEvent {}
  record Restarting(RecognizerRestartReason reason) implements RecognizerEvent {}
  record DebugImageSubmitted(BufferedImage image) implements RecognizerEvent {}
}
