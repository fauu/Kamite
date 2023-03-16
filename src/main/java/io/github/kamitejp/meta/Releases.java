package io.github.kamitejp.meta;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.HTTP;

public final class Releases {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String PAGE_URL = "https://github.com/fauu/Kamite/releases";

  private static final URI MATEST_RELEASE_API_ENPOINT =
    URI.create("https://api.github.com/repos/fauu/Kamite/releases/latest");
  private static final Pattern RESPONSE_VERSION_RE =
    Pattern.compile("\"tag_name\": *?\"v(\\d+\\.\\d+)\"");

  private Releases() {}

  public enum NewCheckResult {
    AVAILABLE,
    NOT_AVAILABLE,
    FAILED;
  }

  public static NewCheckResult checkNewAvailable(Version.Release presentVersion) {
    var req = HttpRequest.newBuilder()
      .uri(MATEST_RELEASE_API_ENPOINT)
      .GET()
      .build();

    try {
      var res = HTTP.client().send(req, BodyHandlers.ofString());
      var m = RESPONSE_VERSION_RE.matcher(res.body());
      if (!m.find()) {
        throw new IllegalArgumentException("Could not find version string in network response");
      }
      var latestVersion = Version.Release.fromMajorMinorString(m.group(1));
      return latestVersion.compareTo(presentVersion) == 1
        ? NewCheckResult.AVAILABLE
        : NewCheckResult.NOT_AVAILABLE;
    } catch (IOException e) {
      LOG.debug("Exception while getting latest release information", e);
    } catch (InterruptedException e) {
      LOG.debug("Interrupted while getting latest release information", e);
    }

    return NewCheckResult.FAILED;
  }
}
