package io.github.kamitejp.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.kamitejp.Env;
import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.server.outmessage.OutMessage;
import io.github.kamitejp.server.outmessage.NotificationOutMessage;
import io.github.kamitejp.util.JSON;
import io.javalin.Javalin;
import io.javalin.core.util.JavalinException;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

public class Server {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String CUSTOM_CSS_FILE_PATH_RELATIVE = "custom.css";
  private static final long WS_IDLE_TIMEOUT_MS = 2 * 60 * 60 * 1000; // 2 hours
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
      config.maxRequestSize = 10_000_000L; // Bumped for image recognition requests
      config.enableCorsForAllOrigins();
      if (Env.isDevMode()) {
        config.enableDevLogging();
      }
      if (!Env.isDevMode() || serveStaticInDevMode) {
        config.addStaticFiles("/web", Location.CLASSPATH);
      }
      config.wsFactoryConfig(factory -> {
        factory.getPolicy().setIdleTimeout(WS_IDLE_TIMEOUT_MS);
      });
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

  public void notify(NotificationKind kind, String content) {
    send(new NotificationOutMessage(kind, content));
  }

  public void send(OutMessage message) {
    String messageJson = null;
    try {
      messageJson = JSON.mapper().writeValueAsString(message);
    } catch(JsonProcessingException e) {
      LOG.debug("Error while serializing message: {}", e.toString()); // NOPMD
    }
    if (messageJson == null) {
      LOG.debug("Tried to send an empty message of kind: {}", message.getKind()); // NOPMD
      return;
    }
    eventCb.accept(new ServerEvent.AboutToSendMessage(message));
    if (wsClientContext == null) {
      LOG.debug( // NOPMD
        "Tried to send a message of kind '{}', but the client was not connected",
        message.getKind()
      );
      return;
    }
    wsClientContext.send(messageJson);
    LOG.debug("Sent a '{}' message", message.getKind()); // NOPMD
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
      LOG.debug("Custom CSS file not found", e);
      throw new NotFoundResponse(); // NOPMD
    }
  }

  private void setupWS(WsConfig ws) {
    ws.onConnect(this::handleClientConnect);
    ws.onMessage(this::handleClientMessage);
    ws.onClose(ctx -> {
      LOG.info("Client websocket connection closed: code {}", ctx.status()); // NOPMD
      // We juggle the contexts like this because we want to set `wsClientContext` to null here, but
      // only if this close event isn't the result of a new client connection replacing the old one.
      // And the status code doesn't let us distinguish that case from, e.g., a browser crash
      if (wsPendingClientContext != null) {
        wsClientContext = wsPendingClientContext;
        wsPendingClientContext = null;
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
      wsClientContext.closeSession(WS_CLOSE_CODE_SUPERSEDED_BY_ANOTHER_CLIENT, null);
    } else {
      wsClientContext = ctx;
    }
    eventCb.accept(new ServerEvent.ClientConnected());
  }

  private void handleClientMessage(WsMessageContext ctx) {
    var messageParseRes = InMessage.fromJSON(ctx.message());
    if (messageParseRes.isErr()) {
      LOG.warn("Error parsing client message: {}", messageParseRes.err()); // NOPMD
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
    var params = ctx.formParam("params");
    eventCb.accept(new ServerEvent.CommandReceived(
      new IncomingCommand.Segmented(
        new IncomingCommand.Kind.Segmented(group, name),
        new IncomingCommand.Params.RawJSON(params)
      )
    ));
  }
}
