package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.config.Config;

public class ConfigOutMessage extends BaseOutMessage {
  private final Config config;

  public ConfigOutMessage(Config config) {
    super("config");
    this.config = config;
  }

  public Config getConfig() {
    return this.config;
  }
}
