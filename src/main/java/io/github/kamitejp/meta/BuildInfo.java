package io.github.kamitejp.meta;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import io.github.kamitejp.util.Strings;

public final class BuildInfo {
  private static final String GIT_PROPERTIES_RESOURCE_PATH = "/git.properties";
  private static final String MAIN_BRANCH_NAME = "master";
  private static final char RELEASE_VERSION_PREFIX = 'v';

  private final String branch;
  private final String commitIDAbbr;
  private final String[] tags;
  private Version version;

  private BuildInfo(String branch, String commitIDAbbr, String tags) {
    if (Strings.isNullOrEmpty(branch) || Strings.isNullOrEmpty(commitIDAbbr) || tags == null) {
      throw new IllegalArgumentException("BuildInfo: required parameter null or empty");
    }
    this.branch = branch;
    this.commitIDAbbr = commitIDAbbr;
    this.tags = tags.split(",");
  }

  public static BuildInfo read() {
    try (var propsInputStream = BuildInfo.class.getResourceAsStream(GIT_PROPERTIES_RESOURCE_PATH)) {
      if (propsInputStream == null) {
        throw new RuntimeException("Failed to read git properties file");
      }

      var props = new Properties();
      props.load(propsInputStream);

      return new BuildInfo(
        props.getProperty("git.branch"),
        props.getProperty("git.commit.id.abbrev"),
        props.getProperty("git.tags")
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to load properties from git properties file", e);
    }
  }

  public Version getVersion() {
    if (version == null) {
      version = generateVersion();
    }
    return version;

  }

  private Version generateVersion() {
    if (MAIN_BRANCH_NAME.equals(branch)) {
      var maybeVersionTag = Arrays.stream(tags)
        .filter(t -> !t.isEmpty() && t.charAt(0) == RELEASE_VERSION_PREFIX)
        .findFirst();
      if (maybeVersionTag.isPresent()) {
        return Version.Release.fromMajorMinorString(maybeVersionTag.get().substring(1));
      }
    }
    return new Version.Dev(commitIDAbbr);
  }
}
