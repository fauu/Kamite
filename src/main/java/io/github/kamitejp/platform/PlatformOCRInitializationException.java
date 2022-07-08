package io.github.kamitejp.platform;

import java.util.List;

public class PlatformOCRInitializationException extends Exception {
  private PlatformOCRInitializationException() {}

  private PlatformOCRInitializationException(String message) {
    super(message);
  }

  public static class MissingDependencies extends PlatformOCRInitializationException {
    private final List<String> dependencies;

    public MissingDependencies(String dependency) {
      this.dependencies = List.of(dependency);
    }

    public MissingDependencies(List<String> dependencies) {
      this.dependencies = dependencies;
    }

    public List<String> getDependencies() {
      return dependencies;
    }
  }

  public static class ScreenshotAPICommunicationFailure extends PlatformOCRInitializationException {
    public ScreenshotAPICommunicationFailure(String message) {
      super(message);
    }
  }
}
