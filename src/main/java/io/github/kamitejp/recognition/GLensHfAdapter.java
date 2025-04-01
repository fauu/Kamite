package io.github.kamitejp.recognition;

public class GLensHfAdapter extends BaseHfOcrAdapter {
  public GLensHfAdapter() {
    super(
      /* hfSpaceID */               "akiraakirasharika-glens",
      /* endpointPath */            "/run/run",
      /* requestExtraPayload */     "",
      /* responseTrimStartMarker */ "\"data\":[\"",
      /* responseTrimEndMarker */   "\",{\"language\""
    );
  }
}
