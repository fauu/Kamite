package io.github.kamitejp.meta;

public sealed interface Version
  permits Version.Dev,
          Version.Release {
  record Dev(String commitIDAbbr) implements Version {
    @Override
    public String toString() {
      return "git-%s".formatted(commitIDAbbr);
    }
  }

  record Release(int major, int minor) implements Version, Comparable<Version.Release> {
    @Override
    public String toString() {
      return "%d.%d".formatted(major, minor);
    }

    static Release fromMajorMinorString(String s) {
      var segs = s.split("\\.");
      if (segs.length != 2) {
        throw new IllegalArgumentException("Major-minor version string must have two segments");
      }
      try {
        var major = Integer.parseInt(segs[0]);
        var minor = Integer.parseInt(segs[1]);
        return new Release(major, minor);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Major-minor version string segments must be integers");
      }
    }

    @Override
    public int compareTo(Version.Release o) {
      if (major > o.major) {
        return 1;
      } else if (major < o.major) {
        return -1;
      } else {
        if (minor > o.minor) {
          return 1;
        } else if (minor < o.minor) {
          return -1;
        } else {
          return 0;
        }
      }
    }
  }
}
