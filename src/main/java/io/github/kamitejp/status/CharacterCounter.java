package io.github.kamitejp.status;

import java.util.regex.Pattern;

import io.github.kamitejp.chunk.ProcessedChunk;

public final class CharacterCounter {
  private static final Pattern COUNTED_CHARACTER_RE = Pattern.compile("\\p{L}|\\p{N}|\\p{S}");

  private int count;
  private boolean frozen;
  private String lastCountedContent;

  public void register(ProcessedChunk c) {
    if (isFrozen()) {
      return;
    }
    if (c.content().equals(lastCountedContent)) {
      return;
    }
    count += COUNTED_CHARACTER_RE.matcher(c.content()).results().count();
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
