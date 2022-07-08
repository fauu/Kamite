package io.github.kamitejp.server.outmessage;

public class ResponseOutMessage extends BaseOutMessage {
  private final long requestTimestamp;
  private final OutMessage innerMessage;

  public ResponseOutMessage(long requestTimestamp, OutMessage innerMessage) {
    super("response");
    this.requestTimestamp = requestTimestamp;
    this.innerMessage = innerMessage;
  }

  public long getRequestTimestamp() {
    return this.requestTimestamp;
  }

  public OutMessage getInnerMessage() {
    return this.innerMessage;
  }
}
