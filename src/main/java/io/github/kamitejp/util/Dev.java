package io.github.kamitejp.util;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum Dev {
  INSTANCE;

  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private final Object lock = new Object();

  private String measurementName;
  private Long startTime;

  @SuppressWarnings("unused")
  public void measureStart(String name) {
    synchronized (lock) {
      if (startTime != null) {
        LOG.debug("measureStart() called twice");
      } else {
        measurementName = name;
        startTime = System.currentTimeMillis();
      }
    }
  }

  @SuppressWarnings("unused")
  public void measureEnd() {
    synchronized (lock) {
      if (startTime == null) {
        LOG.debug("measureEnd() called before measureStart()");
      } else {
        LOG.debug(
          "Measurement `{}`: {} ms",
          measurementName,
          System.currentTimeMillis() - startTime
        );
        startTime = null;
      }
    }
  }
}
