package io.github.kamitejp;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public final class BuildInfo {
  private static final String GIT_PROPERTIES_RESOURCE_PATH = "/git.properties";

  private String branch;
  private String commitIDAbbr;
  private String[] tags;
  private String version;

  private BuildInfo(String branch, String commitIDAbbr, String tags) {
    if (
      branch == null || branch.isEmpty()
      || commitIDAbbr == null || commitIDAbbr.isEmpty()
      || tags == null
    ) {
      throw new IllegalArgumentException(
        "Tried to create a BuildInfo object with a null or empty required parameter"
      );
    }
    this.branch = branch;
    this.commitIDAbbr = commitIDAbbr;
    this.tags = tags.split(",");
  }

  private String generateVersion() {
    if ("master".equals(branch)) {
      var maybeVersionTag = Arrays.stream(tags).filter(t -> t.startsWith("v")).findFirst();
      if (maybeVersionTag.isPresent()) {
        return maybeVersionTag.get().substring(1);
      }
    }
    return "git-%s".formatted(commitIDAbbr);
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

  public String getVersion() {
    if (version == null) {
      version = generateVersion();
    }
    return version;
  }
}
