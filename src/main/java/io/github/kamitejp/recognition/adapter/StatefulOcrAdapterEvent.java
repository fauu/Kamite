package io.github.kamitejp.recognition.adapter;

public sealed interface StatefulOcrAdapterEvent
    permits StatefulOcrAdapterEvent.Launching,
            StatefulOcrAdapterEvent.Launched,
            StatefulOcrAdapterEvent.StartedExtraSetup,
            StatefulOcrAdapterEvent.Initialized,
            StatefulOcrAdapterEvent.TimedOutAndRestarting,
            StatefulOcrAdapterEvent.FailedFatally {
  record Launching(String msg) implements StatefulOcrAdapterEvent {}
  record Launched(String msg) implements StatefulOcrAdapterEvent {}
  record StartedExtraSetup(String msg) implements StatefulOcrAdapterEvent {}
  record Initialized(String msg) implements StatefulOcrAdapterEvent {}
  record TimedOutAndRestarting(String msg) implements StatefulOcrAdapterEvent {}
  record FailedFatally(String msg) implements StatefulOcrAdapterEvent {}
}

