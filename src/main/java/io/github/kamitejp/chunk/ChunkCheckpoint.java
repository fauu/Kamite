package io.github.kamitejp.chunk;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkCheckpoint {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int DELAYED_ALLOW_EXTRA_DELAY_MS = 100;

  private final int throttleIntervalMS;
  private final Consumer<IncomingChunkText> onAllowedThrough;
  private long lastTimestamp;
  private IncomingChunkText lastChunk;

  public ChunkCheckpoint(int throttleIntervalMS, Consumer<IncomingChunkText> onAllowedThrough) {
    this.throttleIntervalMS = throttleIntervalMS;
    this.onAllowedThrough = onAllowedThrough;
  }

  public void register(IncomingChunkText chunk) {
    var ts = System.currentTimeMillis();
    var allow = ts - lastTimestamp >= throttleIntervalMS;
    if (allow) {
      onAllowedThrough.accept(chunk);
    } else {
      // Allow if this turns out to be the last chunk in the rapid succession series
      var delay = throttleIntervalMS + DELAYED_ALLOW_EXTRA_DELAY_MS;
      CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
        if (chunk == lastChunk) {
          onAllowedThrough.accept(chunk);
        } else {
          LOG.debug("Rejected a chunk due to throttling rules");
        }
      });
    }
    lastTimestamp = ts;
    lastChunk = chunk;
  }
}
