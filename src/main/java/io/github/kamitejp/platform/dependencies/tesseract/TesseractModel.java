package io.github.kamitejp.platform.dependencies.tesseract;

public enum TesseractModel {
  VERTICAL("jpn_kmt_vert", "5"),
  VERTICAL_ALT("jpn_kmt_alt_vert", "5"),
  HORIZONTAL("jpn_kmt", "6");

  public final String lang;
  public final String psm;

  TesseractModel(String lang, String psm) {
    this.lang = lang;
    this.psm = psm;
  }
}
