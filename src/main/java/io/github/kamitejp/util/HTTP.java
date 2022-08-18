package io.github.kamitejp.util;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HTTP {
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

  private static HttpClient defaultClient;
  private static final Object lock = new Object();

  private HTTP() {}

  public static HttpClient client() {
    synchronized (lock) {
      if (defaultClient == null) {
        defaultClient = createDefaultClient();
      }
    }
    return defaultClient;
  }

  private static HttpClient createDefaultClient() {
    return HttpClient.newBuilder()
      .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
      .version(HttpClient.Version.HTTP_2)
      .build();
  }
}
