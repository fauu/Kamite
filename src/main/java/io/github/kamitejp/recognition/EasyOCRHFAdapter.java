package io.github.kamitejp.recognition;

import java.lang.invoke.MethodHandles;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.Result;

public class EasyOCRHFAdapter extends BaseHFOCRAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern OCR_RESPONSE_TEXT_FRAGMENT_RE = Pattern.compile("\"([^\"]*)\"");

  public EasyOCRHFAdapter() {
    super(
      /* hfSpaceID */               "tomofi-easyocr",
      /* requestExtraPayload */     ", [\"ja\"]",
      /* responseTrimStartMarker */ "\"data\":[[",
      /* responseTrimEndMarker */   "]]}]"
    );
  }

  @Override
  protected Result<String, RemoteOCRRequestError> trimmedResponseToOCRText(String res) {
    var ocrTextBuilder = new StringBuilder();
    var m = OCR_RESPONSE_TEXT_FRAGMENT_RE.matcher(res);
    while (m.find()) {
      ocrTextBuilder.append(m.group(1));
    }
    return Result.Ok(ocrTextBuilder.toString());
  }
}
