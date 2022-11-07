package io.github.kamitejp.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Executor {
  private static ExecutorService service;
  private static final Object lock = new Object();

  private Executor() {}

  public static ExecutorService get() {
    synchronized (lock) {
      if (service == null) {
        service = Executors.newVirtualThreadPerTaskExecutor();
      }
    }
    return service;
  }

  public static void destroy() {
    synchronized (lock) {
      if (service != null) {
        service.shutdown();
      }
    }
  }
}
