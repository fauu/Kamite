package io.github.kamitejp.recognition;

import java.util.function.BiConsumer;

public abstract class StatefulOCRAdapter {
  protected boolean isReady;

  private int id;
  private BiConsumer<Integer, OCRAdapterEvent> eventCb;

  public void init(int id, BiConsumer<Integer, OCRAdapterEvent> eventCb) {
    this.id = id;
    this.eventCb = eventCb;
    doInit();
  };

  protected abstract void doInit();

  protected final void dispatchEvent(OCRAdapterEvent event) {
    eventCb.accept(id, event);
  }
}
