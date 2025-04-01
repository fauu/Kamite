package io.github.kamitejp.platform;

import java.util.List;

public class PlatformOcrInfrastructureInitializationException extends Exception {
  private PlatformOcrInfrastructureInitializationException() {}

  private PlatformOcrInfrastructureInitializationException(String message) {
    super(message);
  }

  public static class MissingDependencies extends PlatformOcrInfrastructureInitializationException {
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

  public static class ScreenshotAPICommunicationFailure extends PlatformOcrInfrastructureInitializationException {
    public ScreenshotAPICommunicationFailure(String message) {
      super(message);
    }
  }
}
