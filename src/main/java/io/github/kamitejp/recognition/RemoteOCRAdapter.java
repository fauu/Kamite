package io.github.kamitejp.recognition;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;

import io.github.kamitejp.util.Result;

public interface RemoteOCRAdapter<R extends OCRAdapterOCRParams> extends OCRAdapter<R> {
  public static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  // How many seconds to wait before each retry of remote OCR request.
  public static final List<Duration> REMOTE_OCR_RETRY_INTERVALS =
    List.of(Duration.ofSeconds(4), Duration.ofSeconds(7));
  public static final int REMOTE_OCR_MAX_ATTEMPTS = REMOTE_OCR_RETRY_INTERVALS.size() + 1;

  Result<BoxRecognitionOutput, RemoteOCRError> recognize(BufferedImage img, R params);

  default Result<BoxRecognitionOutput, RemoteOCRError> recognizeWithRetry(
    BufferedImage img,
    R params
  ) {
    Result<BoxRecognitionOutput, RemoteOCRError> res = null;
    var mightAttempt = true;
    for (var attemptNo = 0; mightAttempt && attemptNo < REMOTE_OCR_MAX_ATTEMPTS; attemptNo++) {
      if (attemptNo > 0) {
        try {
          Thread.sleep(REMOTE_OCR_RETRY_INTERVALS.get(attemptNo - 1));
        } catch (InterruptedException e) {
          LOG.debug("Interrupted while waiting to retry remote OCR request");
        }
        LOG.info("Retrying remote OCR request");
      }
      res = this.recognize(img, params);
      mightAttempt = false;
      if (res.isErr()) {
        var msg = switch (res.err()) {
          case RemoteOCRError.Timeout _ -> {
            mightAttempt = true;
            yield "HTTP request timed out";
          }
          case RemoteOCRError.SendFailed err -> {
            mightAttempt = true;
            yield "HTTP client send execution has failed: %s".formatted(err.exceptionMessage());
          }
          case RemoteOCRError.Unauthorized _ ->
            "Received `Unauthorized` response. The provided API key is likely invalid";
          case RemoteOCRError.UnexpectedStatusCode err ->
            "Received unexpected status code: %s".formatted(err.code());
          case RemoteOCRError.Other err ->
            err.error();
        };
        LOG.error("Remote OCR service error: {}", msg);
      }
    }
    if (res.isErr()) {
      return res;
    }

    var boxRecognitionOutput = res.get();
    // XXX: Move up?
    // if (text.isBlank()) {
    //   return Result.Err(RecognitionOpError.ZERO_VARIANTS);
    // }

    return Result.Ok(boxRecognitionOutput);
  }
}