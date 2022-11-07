package io.github.kamitejp.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Executor {
  private static ExecutorService service;

  private Executor() {}

  public static ExecutorService get() {
    if (service == null) {
      service = Executors.newVirtualThreadPerTaskExecutor();
    }
    return service;
  }

  public static void destroy() {
    if (service != null) {
      service.shutdown();
    }
  }
}
