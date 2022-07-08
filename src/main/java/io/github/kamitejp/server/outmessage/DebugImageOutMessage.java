package io.github.kamitejp.server.outmessage;

public class DebugImageOutMessage extends BaseOutMessage {
  private final String imgB64;

  public DebugImageOutMessage(String imgBase64) {
    super("debug-image");
    this.imgB64 = imgBase64;
  }

  public String getImgB64() {
    return this.imgB64;
  }
}
