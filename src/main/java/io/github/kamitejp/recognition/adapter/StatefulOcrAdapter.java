package io.github.kamitejp.recognition.adapter;

import java.util.function.BiConsumer;

public abstract class StatefulOcrAdapter {
  protected boolean isReady;

  private int id;
  private BiConsumer<Integer, OcrAdapterEvent> eventCb;

  public void init(int id, BiConsumer<Integer, OcrAdapterEvent> eventCb) {
    this.id = id;
    this.eventCb = eventCb;
    doInit();
  };

  public int getID() {
    return id;
  }

  protected abstract void doInit();

  protected final void dispatchEvent(OcrAdapterEvent event) {
    eventCb.accept(id, event);
  }
}
