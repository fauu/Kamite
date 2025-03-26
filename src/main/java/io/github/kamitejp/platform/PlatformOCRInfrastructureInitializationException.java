package io.github.kamitejp.platform;

import java.util.List;

public class PlatformOCRInfrastructureInitializationException extends Exception {
  private PlatformOCRInfrastructureInitializationException() {}

  private PlatformOCRInfrastructureInitializationException(String message) {
    super(message);
  }

  public static class MissingDependencies extends PlatformOCRInfrastructureInitializationException {
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

  public static class ScreenshotAPICommunicationFailure extends PlatformOCRInfrastructureInitializationException {
    public ScreenshotAPICommunicationFailure(String message) {
      super(message);
    }
  }
}
