package io.github.kamitejp.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class SessionTimer {
  private ZonedDateTime currentStartTime;
  private Duration accumulatedTime = Duration.ZERO;

  private SessionTimer() {}

  public static SessionTimer startingNow() {
    var res = new SessionTimer();
    res.reset();
    return res;
  }

  public static SessionTimer pausedAtZero() {
    return new SessionTimer();
  }

  public void stop() {
    var elapsedSinceCurrentStart = Duration.between(currentStartTime, ZonedDateTime.now());
    currentStartTime = null;
    accumulatedTime = accumulatedTime.plus(elapsedSinceCurrentStart);
  }

  public void start() {
    currentStartTime = ZonedDateTime.now();
  }

  public void toggle() {
    if (isRunning()) {
      stop();
    } else {
      start();
    }
  }

  public void reset() {
    currentStartTime = ZonedDateTime.now();
    accumulatedTime = Duration.ZERO;
  }

  public Optional<ZonedDateTime> getCurrentStartTime() {
    return Optional.ofNullable(currentStartTime);
  }

  public Duration getAccumulatedTime() {
    return accumulatedTime;
  }

  public boolean isRunning() {
    return currentStartTime != null;
  }
}
