package io.github.kamitejp.recognition;

// XXX: Log the events somewhere
public sealed interface OCRAdapterEvent
  permits OCRAdapterEvent.Launching,
          OCRAdapterEvent.Launched,
          OCRAdapterEvent.StartedExtraSetup,
          OCRAdapterEvent.Initialized,
          OCRAdapterEvent.TimedOutAndRestarting,
          OCRAdapterEvent.FailedFatally {
  record Launching(String msg) implements OCRAdapterEvent {}
  record Launched(String msg) implements OCRAdapterEvent {}
  record StartedExtraSetup(String msg) implements OCRAdapterEvent {}
  record Initialized(String msg) implements OCRAdapterEvent {}
  record TimedOutAndRestarting(String msg) implements OCRAdapterEvent {}
  record FailedFatally(String msg) implements OCRAdapterEvent {}
}

