package io.github.kamitejp.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class SessionTimer {
  private ZonedDateTime currentStartTime;
  private Duration accumulatedTime;

  private SessionTimer() {}

  public static SessionTimer startingNow() {
    var res = new SessionTimer();
    res.reset();
    return res;
  }

  public void pause() {
    var elapsedSinceCurrentStart = Duration.between(currentStartTime, ZonedDateTime.now());
    currentStartTime = null;
    accumulatedTime = accumulatedTime.plus(elapsedSinceCurrentStart);
  }

  public void resume() {
    currentStartTime = ZonedDateTime.now();
  }

  public void togglePause() {
    if (isRunning()) {
      pause();
    } else {
      resume();
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
