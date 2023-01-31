package io.github.kamitejp.util;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum Dev {
  INSTANCE;

  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Object lock = new Object();

  private String name;
  private Long startTime;

  public void measureStart(String name) {
    synchronized (lock) {
      if (startTime != null) {
        LOG.debug("measureStart() called twice");
      } else {
        this.name = name;
        startTime = System.currentTimeMillis();
      }
    }
  }

  public void measureEnd() {
    synchronized (lock) {
      if (startTime == null) {
        LOG.debug("measureEnd() called before measureStart()");
      } else {
        LOG.debug("Measurement `{}`: {} ms", name, System.currentTimeMillis() - startTime);
        startTime = null;
      }
    }
  }
}
