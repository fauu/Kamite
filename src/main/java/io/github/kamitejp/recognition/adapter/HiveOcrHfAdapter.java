package io.github.kamitejp.recognition.adapter;

import java.lang.invoke.MethodHandles;

import io.github.kamitejp.recognition.BoxRecognitionOutput;
import io.github.kamitejp.recognition.RemoteOcrError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.util.Result;

public class HiveOcrHfAdapter extends BaseHfOcrAdapter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public HiveOcrHfAdapter() {
    super(
        /* hfSpaceID */               "seaoctopusredchicken-hive-ocr-simple",
        /* requestExtraPayload */     "",
        /* responseTrimStartMarker */ "\"data\":[\"",
        /* responseTrimEndMarker */   "\",[{\"text");
  }

  @Override
  protected Result<BoxRecognitionOutput, RemoteOcrError> trimmedResponseToOcrText(String res) {
    // QUAL: Ad-hoc fix
    res = res.replaceAll("\\\\n", "\n");
    return Result.Ok(BoxRecognitionOutput.fromString(res));
  }
}
