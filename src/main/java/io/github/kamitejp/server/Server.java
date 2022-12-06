package io.github.kamitejp.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.kamitejp.Env;
import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.server.outmessage.OutMessage;
import io.github.kamitejp.server.outmessage.UserNotificationOutMessage;
import io.github.kamitejp.util.JSON;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.util.JavalinException;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

public class Server {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String CUSTOM_CSS_FILE_PATH_RELATIVE = "custom.css";
  private static final Duration WS_IDLE_TIMEOUT = Duration.ofHours(2);
  private static final int WS_CLOSE_CODE_SUPERSEDED_BY_ANOTHER_CLIENT = 4000;

  private Javalin javalinInstance;
  private WsContext wsClientContext;
  private WsContext wsPendingClientContext; // The connection that's about to replace the prev. one
  private final Path configDirPath;
  private Consumer<ServerEvent> eventCb;

  public Server(Path configDirPath) {
    this.configDirPath = configDirPath;
  }

  public void run(
    int port, boolean serveStaticInDevMode, Consumer<ServerEvent> eventCb
  ) throws ServerStartException {
    this.eventCb = eventCb;

    javalinInstance = Javalin.create(config -> {
      config.http.maxRequestSize = 10_000_000L; // Bumped for image recognition requests
      if (Env.isDevMode()) {
        config.plugins.enableDevLogging();
      }
      if (!Env.isDevMode() || serveStaticInDevMode) {
        config.staticFiles.add("/web", Location.CLASSPATH);
      }
      config.jetty.wsFactoryConfig(wsFactory -> wsFactory.setIdleTimeout(WS_IDLE_TIMEOUT));
    })
      .events(event -> {
        event.serverStarted(() -> {
          LOG.info("Started backend web server (port {})", port);
          eventCb.accept(new ServerEvent.Started());
        });
      })
      .get("/custom.css", this::handleGetCustomCSS)
      .post("/cmd/{group}/{name}", this::handleCommandPost)
      .exception(Exception.class, (e, ctx) -> {
        LOG.error("Could not handle HTTP request. See stderr for the stack trace");
        e.printStackTrace();
      })
      .ws("/ws", this::setupWS)
      .wsException(Exception.class, (e, ctx) -> {
        LOG.error("Could not handle WS request. See stderr for the stack trace");
        e.printStackTrace();
      });

    try {
      javalinInstance.start(port);
    } catch (JavalinException e) {
      throw new ServerStartException(e);
    }
  }

  public void notifyUser(UserNotificationKind kind, String content) {
    send(new UserNotificationOutMessage(kind, content));
  }

  public void send(OutMessage message) {
    if (wsPendingClientContext != null) {
      // This is normally done in the old connecton's `onClose()`, but that doesn't always fire
      // (e.g., browser crash)
      this.finalizeReplacementConnection();
    }

    String messageJson = null;
    try {
      messageJson = JSON.mapper().writeValueAsString(message);
    } catch(JsonProcessingException e) {
      LOG.debug("Error while serializing message: {}", e::toString);
    }
    if (messageJson == null) {
      LOG.debug("Tried to send an empty message of kind: {}", message::getKind);
      return;
    }
    eventCb.accept(new ServerEvent.AboutToSendMessage(message));
    if (wsClientContext == null) {
      LOG.debug(
        "Tried to send a message of kind '{}', but the client was not connected",
        () -> message.getKind()
      );
      return;
    }
    wsClientContext.send(messageJson);
    LOG.debug("Sent a '{}' message", message::getKind);
  }

  public void destroy() {
    if (javalinInstance != null) {
      javalinInstance.stop();
      LOG.debug("Stopped Javalin instance");
    }
  }

  private void handleGetCustomCSS(Context ctx) {
    if (configDirPath == null) {
      throw new NotFoundResponse();
    }
    try {
      var cssPath = configDirPath.resolve(CUSTOM_CSS_FILE_PATH_RELATIVE).toString();
      ctx.contentType("text/css").result(new FileInputStream(cssPath));
    } catch (FileNotFoundException e) {
      LOG.debug("Custom CSS file not found", e::toString);
      throw new NotFoundResponse(); // NOPMD
    }
  }

  private void setupWS(WsConfig ws) {
    ws.onConnect(this::handleClientConnect);
    ws.onMessage(this::handleClientMessage);
    ws.onClose(ctx -> {
      LOG.info("Client websocket connection closed: code {}", ctx::status);
      if (wsPendingClientContext != null) {
        this.finalizeReplacementConnection();
      } else {
        wsClientContext = null;
      }
    });
    ws.onError(ctx -> LOG.info("Client websocket connection error: {}", ctx.error().toString()));
  }

  private void handleClientConnect(WsConnectContext ctx) {
    if (wsClientContext != null) {
      LOG.info("Dropping previous client websocket connection");
      wsPendingClientContext = ctx;
      // The remaining new connection setup will be handled in onClose() of the disconnected client
      wsClientContext.closeSession(WS_CLOSE_CODE_SUPERSEDED_BY_ANOTHER_CLIENT, null);
    } else {
      wsClientContext = ctx;
      eventCb.accept(new ServerEvent.ClientConnected());
    }
  }

  private void finalizeReplacementConnection() {
    wsClientContext = wsPendingClientContext;
    wsPendingClientContext = null;
    eventCb.accept(new ServerEvent.ClientConnected());
  }

  private void handleClientMessage(WsMessageContext ctx) {
    var messageParseRes = InMessage.fromJSON(ctx.message());
    if (messageParseRes.isErr()) {
      LOG.warn("Error parsing client message: {}", messageParseRes::err);
    }
    eventCb.accept(new ServerEvent.MessageReceived(messageParseRes.get()));
  }

  private void handleCommandPost(Context ctx) {
    if (eventCb == null) {
      LOG.warn("Received command through HTTP but there is no handler registered");
      return;
    }
    var group = ctx.pathParam("group");
    var name = ctx.pathParam("name");
    var params = ctx.body();
    eventCb.accept(new ServerEvent.CommandReceived(
      new IncomingCommand.Segmented(
        new IncomingCommand.Kind.Segmented(group, name),
        new IncomingCommand.Params.RawJSON(params)
      )
    ));
  }
}
