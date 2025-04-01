package io.github.kamitejp.recognition;

public class MangaOcrHfAdapter extends BaseHfOcrAdapter {
  public MangaOcrHfAdapter() {
    super(
      /* hfSpaceID */               "detomo-japanese-ocr",
      /* requestExtraPayload */     "",
      /* responseTrimStartMarker */ "\"data\":[\"",
      /* responseTrimEndMarker */   "\"]"
    );
  }
}
