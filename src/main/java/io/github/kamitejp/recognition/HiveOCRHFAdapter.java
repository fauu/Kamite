package io.github.kamitejp.recognition;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HiveOCRHFAdapter extends BaseHFOCRAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public HiveOCRHFAdapter() {
    super(
      /* hfSpaceID */               "seaoctopusredchicken-hive-ocr-simple",
      /* requestExtraPayload */     "",
      /* responseTrimStartMarker */ "\"data\":[\"",
      /* responseTrimEndMarker */   "\",[{\"text"
    );
  }
}
