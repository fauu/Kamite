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

public class AgentClient extends WebSocketAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int CONNECTION_TIMEOUT_MS = 2000;
  private static final int CONNECTION_RETRY_INTERVAL_MS = 2000;

  private final URI wsServerEndpoint;
  private final Consumer<String> chunkCb;
  private final Consumer<String> chunkTranslationCb;
  private WebSocket ws;

  public AgentClient(String host, Consumer<String> chunkCb, Consumer<String> chunkTranslationCb) {
    this.wsServerEndpoint = URI.create("ws://%s".formatted(host));
    this.chunkCb = chunkCb;
    this.chunkTranslationCb = chunkTranslationCb;
    try {
      this.ws = new WebSocketFactory().createSocket(wsServerEndpoint, CONNECTION_TIMEOUT_MS);
      ws.addListener(this);
      LOG.debug("Starting Agent client (endpoint = {})", wsServerEndpoint.toString());
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
  public void onTextMessage(WebSocket _ws, String msgJSON) {
    try {
      var msg = JSON.mapper().readValue(msgJSON, AgentSentenceMessage.class);
      switch (msg.type()) {
        case "copyText"  -> chunkCb.accept(msg.sentence());
        case "translate" -> chunkTranslationCb.accept(msg.sentence());
      }
    } catch (JsonProcessingException e) {
      LOG.error("Exception while parsing message from Agent", e);
    }
  }

  @Override
  public void onConnectError(WebSocket _websocket, WebSocketException _exception) {
    wsWaitAndReconnect();
  }

  @Override
  public void onConnected(WebSocket _ws, Map<String, List<String>> _headers) {
    LOG.info("Connected to Agent");
  }

  @Override
  public void onDisconnected(
    WebSocket _websocket,
    WebSocketFrame _serverCloseFrame,
    WebSocketFrame _clientCloseFrame,
    boolean _closedByServer
  ) {
    LOG.info("Disconnected from Agent");
    wsWaitAndReconnect();
  }

  @Override
  public void onError(WebSocket _websocket, WebSocketException cause) {
    if (cause.getError() == WebSocketError.SOCKET_CONNECT_ERROR) {
      LOG.trace("Socket connect error");
      return;
    }
    LOG.error("Web socket client error. See stderr for the stack trace");
    cause.printStackTrace();
  }
}
