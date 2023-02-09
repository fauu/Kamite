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

public class Releases {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final URI LATEST_RELASE_GITHUB_API_ENPOINT =
    URI.create("https://api.github.com/repos/fauu/kamite/releases/latest");
  private static final Pattern RESPONSE_VERSION_RE =
    Pattern.compile("\"tag_name\": *?\"v(\\d+\\.\\d+)\"");

  static enum NewReleaseCheckResult {
    AVAILABLE,
    NOT_AVAILABLE,
    FAILED;
  }

  public static NewReleaseCheckResult checkNewAvailable(Version.Release presentVersion) {
    var req = HttpRequest.newBuilder()
      .uri(LATEST_RELASE_GITHUB_API_ENPOINT)
      .GET()
      .build();

    try {
      // XXX: Blocking
      var res = HTTP.client().send(req, BodyHandlers.ofString());
      var m = RESPONSE_VERSION_RE.matcher(res.body());
      if (!m.find()) {
        throw new IllegalArgumentException("Could not find version string in Github response");
      }
      var latestVersion = Version.Release.fromMajorMinorString(m.group(1));
      return latestVersion.compareTo(presentVersion) == 1
        ? NewReleaseCheckResult.AVAILABLE
        : NewReleaseCheckResult.NOT_AVAILABLE;
    } catch (IOException e) {
      LOG.debug("Exception while getting latest release information", e);
    } catch (InterruptedException e) {
      // XXX
      e.printStackTrace();
    }

    return NewReleaseCheckResult.FAILED;
  }
}
