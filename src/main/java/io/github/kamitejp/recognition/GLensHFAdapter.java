package io.github.kamitejp.recognition;

public class GLensHFAdapter extends BaseHFOCRAdapter {
  public GLensHFAdapter() {
    super(
      /* hfSpaceID */               "akiraakirasharika-glens",
      /* endpointPath */            "/run/run",
      /* requestExtraPayload */     "",
      /* responseTrimStartMarker */ "\"data\":[\"",
      /* responseTrimEndMarker */   "\",{\"language\""
    );
  }
}
