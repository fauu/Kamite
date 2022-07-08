package io.github.kamitejp.server.outmessage;

public abstract class BaseOutMessage implements OutMessage {
  private String kind;

  protected BaseOutMessage(String kind) {
    this.kind = kind;
  }

  public String getKind() {
    return kind;
  }
}
