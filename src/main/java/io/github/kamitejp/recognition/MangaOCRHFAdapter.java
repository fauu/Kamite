package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.util.HTTP;
import io.github.kamitejp.util.Result;

public class MangaOCRHFAdapter implements RemoteOCRAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int REQUEST_TIMEOUT_S = 8;

  private static final URI API_ENDPOINT =
    URI.create("https://detomo-japanese-ocr.hf.space/api/predict/");
  private static final String OCR_REQUEST_BODY_TPL = "{\"data\": [\"data:%s;base64,%s\"]}";
  private static final String OCR_RESPONSE_TEXT_START_MARKER = "\"data\":[\"";
  private static final String OCR_RESPONSE_TEXT_END_MARKER = "\"]";

  @Override
  @SuppressWarnings("MethodMayBeStatic")
  public Result<String, RemoteOCRRequestError> ocr(BufferedImage img) {
    var reqBodyString = OCR_REQUEST_BODY_TPL.formatted(
      ImageOps.DEFAULT_IMAGE_FORMAT_MIMETYPE, ImageOps.convertToBase64(img)
    );
    var req = HttpRequest.newBuilder()
      .uri(API_ENDPOINT)
      .headers("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(reqBodyString))
      .build();

    HttpResponse<String> res;
    try {
      var resFuture = HTTP.client().sendAsync(req, HttpResponse.BodyHandlers.ofString());
      res = resFuture.get(REQUEST_TIMEOUT_S, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      return Result.Err(new RemoteOCRRequestError.Timeout());
    } catch (ExecutionException | InterruptedException e) {
      return Result.Err(new RemoteOCRRequestError.SendFailed(e.getMessage()));
    }

    var code = res.statusCode();
    if (code != HttpURLConnection.HTTP_OK) {
      return Result.Err(new RemoteOCRRequestError.UnexpectedStatusCode(code));
    }

    var textStartMarkerIdx = res.body().indexOf(OCR_RESPONSE_TEXT_START_MARKER);
    if (textStartMarkerIdx == -1) {
      return Result.Err(
        new RemoteOCRRequestError.Other("Response did not contain the expected text start marker")
      );
    }
    var textStartIdx = textStartMarkerIdx + OCR_RESPONSE_TEXT_START_MARKER.length();
    var textEndMarkerIdx = res.body().indexOf(OCR_RESPONSE_TEXT_END_MARKER, textStartIdx);
    if (textEndMarkerIdx == -1) {
      return Result.Err(
        new RemoteOCRRequestError.Other("Response did not contain the expected text end marker")
      );
    }

    return Result.Ok(res.body().substring(textStartIdx, textEndMarkerIdx));
  }
}
