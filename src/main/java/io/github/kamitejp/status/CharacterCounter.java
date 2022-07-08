package io.github.kamitejp.status;

import io.github.kamitejp.recognition.PostprocessedChunk;

public final class CharacterCounter {
  private int count;
  private boolean frozen;
  private String lastCountedContent;

  public void register(PostprocessedChunk c) {
    if (isFrozen()) {
      return;
    }
    if (c.content().equals(lastCountedContent)) {
      return;
    }
    var contentNoWhitespace = c.content().replaceAll("\\s+", "");
    contentNoWhitespace = contentNoWhitespace.replaceAll("　", "");
    count += contentNoWhitespace.length();
    lastCountedContent = c.content();
  }

  public void toggleFreeze() {
    setFrozen(!isFrozen());
  }

  public void reset() {
    count = 0;
    frozen = false;
  }

  public int getCount() {
    return count;
  }

  public boolean isFrozen() {
    return frozen;
  }

  public void setFrozen(boolean frozen) {
    this.frozen = frozen;
  }
}
