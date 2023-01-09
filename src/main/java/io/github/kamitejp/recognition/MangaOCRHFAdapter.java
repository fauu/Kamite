package io.github.kamitejp.recognition;

public class MangaOCRHFAdapter extends BaseHFOCRAdapter {
  public MangaOCRHFAdapter() {
    super(
      /* hfSpaceID */                  "detomo-japanese-ocr",
      /* requestExtraPayload */        "",
      /* responseExtractStartMarker */ "\"data\":[\"",
      /* responseExtractEndMarker */   "\"]"
    );
  }
}
