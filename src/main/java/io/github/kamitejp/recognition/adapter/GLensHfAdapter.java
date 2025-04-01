package io.github.kamitejp.recognition.adapter;

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
