package io.github.kamitejp.recognition;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.Result;

public class HiveOCRHFAdapter extends BaseHFOCRAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public HiveOCRHFAdapter() {
    super(
      /* hfSpaceID */               "seaoctopusredchicken-hive-ocr-simple",
      /* requestExtraPayload */     "",
      /* responseTrimStartMarker */ "\"data\":[\"",
      /* responseTrimEndMarker */   "\",[{\"text"
    );
  }

  @Override
  protected Result<BoxRecognitionOutput, RemoteOCRError> trimmedResponseToOCRText(String res) {
    // QUAL: Ad-hoc fix
    res = res.replaceAll("\\\\n", "\n");
    return Result.Ok(BoxRecognitionOutput.fromString(res));
  }
}
