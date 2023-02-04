package io.github.kamitejp.platform.agent;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketError;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import io.github.kamitejp.util.JSON;

// TODO: While connected, show status panel indicator in the client
public class AgentClient extends WebSocketAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int CONNECTION_TIMEOUT_MS = 2000;
  private static final int CONNECTION_RETRY_INTERVAL_MS = 2000;

  private final Runnable connectCb;
  private final Runnable disconnectCb;
  private final Consumer<String> chunkCb;
  private final Consumer<String> chunkTranslationCb;
  private WebSocket ws;

  public AgentClient(
    String host,
    Runnable connectCb,
    Runnable disconnectCb,
    Consumer<String> chunkCb,
    Consumer<String> chunkTranslationCb
  ) {
    this.connectCb = connectCb;
    this.disconnectCb = disconnectCb;
    this.chunkCb = chunkCb;
    this.chunkTranslationCb = chunkTranslationCb;

    var wsServerEndpoint = URI.create("ws://%s".formatted(host));
    try {
      this.ws = new WebSocketFactory().createSocket(wsServerEndpoint, CONNECTION_TIMEOUT_MS);
      ws.addListener(this);
      LOG.debug("Starting Agent client (endpoint = {})", wsServerEndpoint);
      ws.connectAsynchronously();
    } catch (IOException e) {
      LOG.error("Exception while creating web socket", e);
      // QUAL: Should return error so that the instance can be discarded
    }
  }

  public void destroy() {
    if (ws != null) {
      ws.disconnect();
    }
  }

  private void wsWaitAndReconnect() {
    try {
      Thread.sleep(CONNECTION_RETRY_INTERVAL_MS);
    } catch (InterruptedException e) {
      LOG.error("Interrupted while waiting for Agent connection. Aborting");
      return;
    }

    try {
      ws.recreate().connectAsynchronously();
    } catch (IOException e) {
      LOG.error("Exception while recreating web socket", e);
    }
  }

  @Override
  public void onTextMessage(WebSocket unusedWS, String msgJSON) {
    try {
      var msg = JSON.mapper().readValue(msgJSON, AgentSentenceMessage.class);
      switch (msg.type()) {
        case "copyText"  -> chunkCb.accept(msg.sentence());
        case "translate" -> chunkTranslationCb.accept(msg.sentence());
        default -> LOG.debug("Received Agent message of unhandled type: {}", msg.type());
      }
    } catch (JsonProcessingException e) {
      LOG.error("Exception while parsing message from Agent", e);
    }
  }

  @Override
  public void onConnectError(WebSocket unusedWS, WebSocketException unusedException) {
    LOG.trace("Connect error");
    wsWaitAndReconnect();
  }

  @Override
  public void onConnected(WebSocket unusedWS, Map<String, List<String>> unusedHeaders) {
    LOG.info("Connected to Agent");
    connectCb.run();
  }

  @Override
  public void onDisconnected(
    WebSocket unusedWS,
    WebSocketFrame unusedServerCloseFrame,
    WebSocketFrame unusedClientCloseFrame,
    boolean unusedClosedByServer
  ) {
    LOG.info("Disconnected from Agent");
    disconnectCb.run();
    wsWaitAndReconnect();
  }

  @Override
  public void onError(WebSocket unusedWebsocket, WebSocketException cause) {
    if (cause.getError() == WebSocketError.SOCKET_CONNECT_ERROR) {
      LOG.trace("Socket connect error");
      return;
    }
    LOG.error("Web socket client error. See stderr for the stack trace");
    cause.printStackTrace();
  }
}
