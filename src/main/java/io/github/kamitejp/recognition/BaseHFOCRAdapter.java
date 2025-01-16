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

public abstract class BaseHFOCRAdapter implements RemoteOCRAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int REQUEST_TIMEOUT_S = 8;

  private static final String API_ENDPOINT_TPL = "https://%s.hf.space/api/predict/";
  private static final String OCR_REQUEST_BODY_TPL = "{\"data\": [\"data:%s;base64,%s\"%s]}";

  private final URI apiEndpoint;
  private final String requestExtraPayload;
  private final String responseTrimStartMarker;
  private final String responseTrimEndMarker;

  public BaseHFOCRAdapter(
    String hfSpaceID,
    String requestExtraPayload,
    String responseTrimStartMarker,
    String responseTrimEndMarker
  ) {
    this.apiEndpoint = URI.create(API_ENDPOINT_TPL.formatted(hfSpaceID));
    this.requestExtraPayload = requestExtraPayload;
    this.responseTrimStartMarker = responseTrimStartMarker;
    this.responseTrimEndMarker = responseTrimEndMarker;
  }

  @Override
  @SuppressWarnings("MethodMayBeStatic")
  public final Result<String, RemoteOCRRequestError> ocr(BufferedImage img) {
    var reqBodyString = OCR_REQUEST_BODY_TPL.formatted(
      ImageOps.DEFAULT_IMAGE_FORMAT_MIMETYPE,
      ImageOps.convertToBase64(img),
      requestExtraPayload
    );
    var req = HttpRequest.newBuilder()
      .uri(apiEndpoint)
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

    String resBody = res.body();
    if (responseTrimStartMarker != null) {
      var textStartMarkerIdx = res.body().indexOf(responseTrimStartMarker);
      if (textStartMarkerIdx == -1) {
        return Result.Err(
          new RemoteOCRRequestError.Other("Response did not contain the expected text start marker")
        );
      }
      var textStartIdx = textStartMarkerIdx + responseTrimStartMarker.length();
      var textEndMarkerIdx = res.body().indexOf(responseTrimEndMarker, textStartIdx);
      if (textEndMarkerIdx == -1) {
        return Result.Err(
          new RemoteOCRRequestError.Other("Response did not contain the expected text end marker")
        );
      }

      resBody = resBody.substring(textStartIdx, textEndMarkerIdx);
    }

    return trimmedResponseToOCRText(resBody);
  }

  protected Result<String, RemoteOCRRequestError> trimmedResponseToOCRText(String res) {
    return Result.Ok(res);
  }
}
