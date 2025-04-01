package io.github.kamitejp.recognition.adapter;

// XXX: Log the events somewhere
public sealed interface OcrAdapterEvent
  permits OcrAdapterEvent.Launching,
          OcrAdapterEvent.Launched,
          OcrAdapterEvent.StartedExtraSetup,
          OcrAdapterEvent.Initialized,
          OcrAdapterEvent.TimedOutAndRestarting,
          OcrAdapterEvent.FailedFatally {
  record Launching(String msg) implements OcrAdapterEvent {}
  record Launched(String msg) implements OcrAdapterEvent {}
  record StartedExtraSetup(String msg) implements OcrAdapterEvent {}
  record Initialized(String msg) implements OcrAdapterEvent {}
  record TimedOutAndRestarting(String msg) implements OcrAdapterEvent {}
  record FailedFatally(String msg) implements OcrAdapterEvent {}
}

