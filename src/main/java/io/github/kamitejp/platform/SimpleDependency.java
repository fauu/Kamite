package io.github.kamitejp.platform;

public interface SimpleDependency {
  String getName();

  boolean checkIsAvailable();
}
