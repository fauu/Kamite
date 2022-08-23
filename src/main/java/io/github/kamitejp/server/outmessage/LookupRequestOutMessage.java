package io.github.kamitejp.server.outmessage;

import java.util.Optional;

public class LookupRequestOutMessage extends BaseOutMessage {
  private final String targetSymbol;
  private final String customText;

  public LookupRequestOutMessage(String targetSymbol, String customText) {
    super("lookup-request");
    this.targetSymbol = targetSymbol;
    this.customText = customText;
  }

  public String getTargetSymbol() {
    return targetSymbol;
  }

  public Optional<String> getCustomText() {
    return Optional.ofNullable(customText);
  }
}
