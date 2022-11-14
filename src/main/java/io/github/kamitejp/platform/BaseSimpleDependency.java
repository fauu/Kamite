package io.github.kamitejp.platform;

public abstract class BaseSimpleDependency implements SimpleDependency {
  protected static final int DEFAULT_AVAILABILITY_CHECK_TIMEOUT_MS = 1000;

  protected final String BIN;
  protected final String NAME;

  public BaseSimpleDependency(String bin) {
    this(bin, bin);
  }

  public BaseSimpleDependency(String bin, String name) {
    this.BIN = bin;
    this.NAME = name;
  }

  public String getName() {
    return NAME;
  }
}
