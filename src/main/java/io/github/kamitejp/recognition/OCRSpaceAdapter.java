package io.github.kamitejp.recognition;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public class OCRSpaceAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final int REQUEST_TIMEOUT_S = 8;
  private static final String MULTIPART_BOUNDARY = "apUaO5xP8REdGOEJnoAy";

  private static final URI API_ENDPOINT = URI.create("https://api.ocr.space/parse/image");
  private static final String PARAM_FILETYPE = "png";
  private static final String PARAM_LANGUAGE = "jpn";
  private static final String PARAM_SCALE = "true";
  private static final String PARAM_IMAGE_FILENAME = "image.png";
  private static final String PARAM_IMAGE_MIMETYPE = "image/png";

  private final String apiKey;
  private final HttpClient httpClient;

  OCRSpaceAdapter(String apiKey) {
    this.apiKey = apiKey;
    httpClient = HttpClient.newBuilder()
      .connectTimeout(CONNECT_TIMEOUT)
      .version(HttpClient.Version.HTTP_2)
      .build();
  }

  private record ImageUpload(String filename, String mimeType, byte[] bytes) {}

  // TODO: Don't send free API request if the image is over 1 MB
  public Result<String, String> ocr(byte[] imageBytes) {
    var data = Map.of(
      "apikey", apiKey,
      "filetype", PARAM_FILETYPE,
      "language", PARAM_LANGUAGE,
      "scale", PARAM_SCALE,
      "file", new ImageUpload(PARAM_IMAGE_FILENAME, PARAM_IMAGE_MIMETYPE, imageBytes)
      // "detectOrientation", "true", TODO: Test this
    );
    
    HttpRequest req;
    try {
      req = HttpRequest.newBuilder()
        .uri(API_ENDPOINT)
        .headers("Content-Type", "multipart/form-data; boundary=%s".formatted(MULTIPART_BOUNDARY))
        .POST(multipartBodyPublisher(data, MULTIPART_BOUNDARY))
        .build();
    } catch (IOException e) {
      return Result.Err("Failed to build HTTP request for OCR.space: %s".formatted(e));
    }

    HttpResponse<String> res;
    try {
      var resFuture = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
      res = resFuture.get(REQUEST_TIMEOUT_S, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      return Result.Err("OCR.space HTTP request timed out");
    } catch (ExecutionException | InterruptedException e) {
      return Result.Err("OCR.space HTTP client send execution has failed: %s".formatted(e));
    }

    var code = res.statusCode();
    if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
      return Result.Err(
        "OCR.space returned status code Unauthorized. The provided API key is likely invalid"
      );
    } else if (code != HttpURLConnection.HTTP_OK) {
      return Result.Err("OCR.space returned unexpected status code: %d".formatted(code));
    }

    JsonNode root;
    //noinspection OverlyBroadCatchBlock
    try {
      root = JSON.mapper().readTree(res.body());
    } catch (JsonProcessingException e) {
      return Result.Err("Failed to read OCR.space response JSON: %s".formatted(e));
    }

    List<JsonNode> parsedResultsEls = null;
    var parsedResultsRoot = root.get("ParsedResults");
    if (parsedResultsRoot != null) {
      parsedResultsEls = StreamSupport
        .stream(parsedResultsRoot.spliterator(), false)
        .collect(toList());
    }

    var errored = root.get("IsErroredOnProcessing").asBoolean();
    if (errored) {
      return Result.Err(
        "OCR.space reported processing error: %s".formatted(root.get("ErrorMessage"))
      );
    }

    if (parsedResultsEls == null) {
      return Result.Ok("");
    }

    var text = parsedResultsEls.stream()
      .map(node -> {
        var t = node.get("ParsedText");
        if (t != null && !t.isNull()) {
          return t.textValue();
        } else {
          return "";
        }
      })
        .collect(joining());
    text = text.replace("\r\n", "\n");

    return Result.Ok(text);
  }

  // https://urvanov.ru/2020/08/18/java-11-httpclient-multipart-form-data/
  private static HttpRequest.BodyPublisher multipartBodyPublisher(
    Map<String, Object> data,
    @SuppressWarnings("SameParameterValue") String boundary
  ) throws IOException {
    @SuppressWarnings({"MagicNumber", "TypeMayBeWeakened"})
    var byteArrays = new ArrayList<byte[]>(40);

    var separator = "--%s\r\nContent-Disposition: form-data; name="
      .formatted(boundary)
      .getBytes(StandardCharsets.UTF_8);
    for (var entry : data.entrySet()) {
      byteArrays.add(separator);

      var key = entry.getKey();
      var value = entry.getValue();
      if (value instanceof String s) {
        byteArrays.add(
          "\"%s\"\r\n\r\n%s\r\n"
            .formatted(key, s)
            .getBytes(StandardCharsets.UTF_8)
        );
      } else if (value instanceof ImageUpload image) {
        byteArrays.add(
          "\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n"
            .formatted(key, image.filename(), image.mimeType())
            .getBytes(StandardCharsets.UTF_8)
        );
        byteArrays.add(image.bytes());
        byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
      }
    }
    byteArrays.add("--%s--".formatted(boundary).getBytes(StandardCharsets.UTF_8));

    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
  }
}
