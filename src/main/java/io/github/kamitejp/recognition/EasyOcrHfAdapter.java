package io.github.kamitejp.recognition;

import java.lang.invoke.MethodHandles;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.Result;

public class EasyOcrHfAdapter extends BaseHfOcrAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern OCR_RESPONSE_TEXT_FRAGMENT_RE = Pattern.compile("\"([^\"]*)\"");

  public EasyOcrHfAdapter() {
    super(
        /* hfSpaceID */               "tomofi-easyocr",
        /* requestExtraPayload */     ", [\"ja\"]",
        /* responseTrimStartMarker */ "\"data\":[[",
        /* responseTrimEndMarker */   "]]}]");
  }

  @Override
  protected Result<BoxRecognitionOutput, RemoteOcrError> trimmedResponseToOcrText(String res) {
    var ocrTextBuilder = new StringBuilder();
    var m = OCR_RESPONSE_TEXT_FRAGMENT_RE.matcher(res);
    while (m.find()) {
      ocrTextBuilder.append(m.group(1));
    }
    return Result.Ok(BoxRecognitionOutput.fromString(ocrTextBuilder.toString()));
  }
}
