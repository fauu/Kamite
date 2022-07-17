package io.github.kamitejp;

import static java.util.stream.Collectors.joining;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.Base64;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.api.Command;
import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.api.Request;
import io.github.kamitejp.chunk.ChunkCheckpoint;
import io.github.kamitejp.chunk.ChunkFilter;
import io.github.kamitejp.chunk.IncomingChunkText;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.config.ConfigManager;
import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.dbus.DBusEvent;
import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.PlatformInitializationException;
import io.github.kamitejp.platform.PlatformOCRInitializationException;
import io.github.kamitejp.platform.RecognitionOpError;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.platform.mpv.MPVCommand;
import io.github.kamitejp.platform.mpv.MPVController;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.recognition.AutoBlockHeuristic;
import io.github.kamitejp.recognition.ChunkVariants;
import io.github.kamitejp.recognition.ImageOps;
import io.github.kamitejp.recognition.OCREngine;
import io.github.kamitejp.recognition.Recognizer;
import io.github.kamitejp.recognition.RecognizerEvent;
import io.github.kamitejp.recognition.RecognizerInitializationException;
import io.github.kamitejp.recognition.RecognizerStatus;
import io.github.kamitejp.recognition.TextOrientation;
import io.github.kamitejp.server.InMessage;
import io.github.kamitejp.server.NotificationKind;
import io.github.kamitejp.server.Server;
import io.github.kamitejp.server.ServerEvent;
import io.github.kamitejp.server.ServerStartException;
import io.github.kamitejp.server.outmessage.ChunkTranslationOutMessage;
import io.github.kamitejp.server.outmessage.ChunkVariantsOutMessage;
import io.github.kamitejp.server.outmessage.ChunkWithFuriganaOutMessage;
import io.github.kamitejp.server.outmessage.ConfigOutMessage;
import io.github.kamitejp.server.outmessage.DebugImageOutMessage;
import io.github.kamitejp.server.outmessage.NotificationOutMessage;
import io.github.kamitejp.server.outmessage.ProgramStatusOutMessage;
import io.github.kamitejp.server.outmessage.ResponseOutMessage;
import io.github.kamitejp.status.CharacterCounter;
import io.github.kamitejp.status.PlayerStatus;
import io.github.kamitejp.status.ProgramStatus;
import io.github.kamitejp.status.SessionTimer;
import io.github.kamitejp.textprocessing.TextProcessor;

public class Kamite {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
  public static final String APP_NAME_DISPLAY = "Kamite";

  private Config config;
  private Server server;
  private Platform platform;
  private OCRDirectoryWatcher ocrDirectoryWatcher;
  private Recognizer recognizer;
  private TextProcessor textProcessor;
  private MPVController mpvController;
  private ChunkCheckpoint chunkCheckpoint;

  private ProgramStatus status;

  public void run(Map<String,String> args, BuildInfo buildInfo) {
    LOG.info("Starting Kamite (version {})", buildInfo.getVersion()); // NOPMD

    var preconfigArgs = processPreconfigArgs(args);

    platform = Platform.createSuitable();
    if (platform == null) {
      createControlGUIAndShowFatalError("Failed to create a suitable platform support object");
      return;
    }

    try {
      platform.init();
    } catch (PlatformInitializationException e) {
      createControlGUIAndShowFatalError("Failed to initialize platform support", e.toString());
      return;
    }
    LOG.info("Initialized support for platform {}", platform.getClass().getSimpleName()); // NOPMD

    // Load config
    var maybeConfigDirPath = platform.getConfigDirPath();
    if (maybeConfigDirPath.isEmpty()) {
      createControlGUIAndShowFatalError("Failed to get platform-specific config path");
      return;
    }
    var configReadRes =
      ConfigManager.read(maybeConfigDirPath.get(), preconfigArgs.profileName(), args);
    if (configReadRes.isErr()) {
      createControlGUIAndShowFatalError("Failed to read config", configReadRes.err());
      return;
    }
    var configReadSuccess = configReadRes.get();
    config = configReadSuccess.config();
    processConfig(preconfigArgs);

    if (config.controlWindow()) {
      createControlGUI();
      LOG.info("Created control window (Kamite version {})", buildInfo.getVersion()); // NOPMD
    }

    // Unsupported platform features message deferred until control GUI potentially present
    var unsupportedFeatures = platform.getUnsupportedFeatures();
    if (unsupportedFeatures.size() > 0) {
      LOG.warn( // NOPMD
        "The current platform does *not* support the following features:\n{}",
        unsupportedFeatures.stream()
          .map(f -> "– %s (%s)".formatted(f.getDisplayName(), f.getDescription()))
          .collect(joining("\n"))
      );
    }

    // Config load message deferred until control GUI potentially present
    if (configReadSuccess.loadedProfileNames() != null) {
      LOG.info( // NOPMD
        "Loaded config profiles: {}",
        String.join(", ", configReadSuccess.loadedProfileNames())
      );
    }

    var ocrWatchDir = config.ocr().watchDir();
    if (ocrWatchDir != null) {
      try {
        ocrDirectoryWatcher = new OCRDirectoryWatcher(ocrWatchDir, this::recognizeBox);
      } catch (OCRDirectoryWatcherCreationException e) {
        LOG.error("Failed to create OCR directory watcher: {}", e.toString());
      }
    }

    // Init DBus communication
    if (platform instanceof LinuxPlatform linuxPlatform) {
      linuxPlatform.getDBusClient().ifPresent((client) -> {
        client.onEvent(this::handleDBusEvent);
        LOG.debug("Connected DBus event handler");
      });
    }

    chunkCheckpoint = new ChunkCheckpoint(
      config.chunk().throttleMS(),
      /* onAllowedThrough */ this::handleShowChunkPostCheckpoint
    );

    textProcessor = new TextProcessor();

    server = new Server(platform.getConfigDirPath().orElse(null));
    try {
      server.run(
        config.server().port(),
        config.dev().serveStaticInDevMode(),
        this::handleServerEvent
      );
    } catch (ServerStartException e) {
      showFatalError("Failed to start backend web server", e.toString());
      return;
    }

    initMPVController();

    initRecognizer();

    Runtime.getRuntime().addShutdownHook(
      new Thread(() -> {
        server.destroy();
        platform.destroy();
        if (ocrDirectoryWatcher != null) {
          ocrDirectoryWatcher.destroy();
        }
        if (recognizer != null) {
          recognizer.destroy();
        }
        mpvController.destroy();
      })
    );
  }

  private static void createControlGUI() {
    (new ControlGUI()).init();
  }

  private static void createControlGUIAndShowFatalError(String message) {
    createControlGUIAndShowFatalError(message, null);
  }

  private static void createControlGUIAndShowFatalError(String message, String details) {
    createControlGUI();
    showFatalError(message, details);
  }

  private static void showFatalError(String message, String details) {
    var detailsPart = details == null ? "" : " Details: %s".formatted(details);
    LOG.error("{}. The program will not continue.{}", message, detailsPart);
  }

  private void initMPVController() {
    mpvController = new MPVController(
      platform,
      /* statusUpdateCb */ (PlayerStatus newStatus) -> {
        status.setPlayerStatus(newStatus);
        sendStatus(ProgramStatusOutMessage.PlayerStatus.class);
      }
    );
  }

  private void initRecognizer() {
    var unavailable = true;
    try {
      var engineRes = OCREngine.uninitializedFromConfig(config);
      if (engineRes.isErr()) {
        LOG.error("Error setting up OCR engine for initialization: {}", engineRes.err());
      } else {
        var engine = engineRes.get();
        if (!(engine instanceof OCREngine.None)) {
          platform.initOCR(engine);
          recognizer = new Recognizer(
            platform,
            engine,
            status.isDebug(),
            this::handleRecognizerEvent
          );
          unavailable = false;
        }
      }
    } catch (PlatformOCRInitializationException.MissingDependencies e) {
      LOG.error( // NOPMD
        "Text recognition will not be available due to missing dependencies: {}",
        String.join(", ", e.getDependencies())
      );
    } catch (PlatformOCRInitializationException e) {
      throw new RuntimeException("Unhandled PlatformOCRInitializationException", e);
    } catch (RecognizerInitializationException e) {
      if (e.getMessage() != null) {
        LOG.error(e.getMessage()); // NOPMD
      } else {
        LOG.error("Could not initialize Recognizer. See stderr for the stack trace");
        e.printStackTrace();
      }
    }
    if (unavailable) {
      updateAndSendRecognizerStatus(RecognizerStatus.Kind.UNAVAILABLE);
    }
  }

  private void handleRecognizerEvent(RecognizerEvent event) {
    switch (event) {
      case RecognizerEvent.Initialized e -> {
        status.updateRecognizerStatus(RecognizerStatus.Kind.IDLE, e.availableCommands());
        sendStatus(ProgramStatusOutMessage.RecognizerStatus.class);
      }
      case RecognizerEvent.MangaOCRStartedDownloadingModel ignored ->
        server.notify(
          NotificationKind.INFO,
          "“Manga OCR” is downloading OCR model. This might take a while…"
        );
      case RecognizerEvent.Crashed ignored -> {
        LOG.info("Recognizer has crashed and will not be restarted");
        notifyError("Recognizer has crashed. Text recognition will be unavailable");
        updateAndSendRecognizerStatus(RecognizerStatus.Kind.UNAVAILABLE);
      }
      case RecognizerEvent.Restarting e -> {
        switch (e.reason()) {
          case MANGA_OCR_TIMED_OUT_AND_RESTARTING -> {
            updateAndSendRecognizerStatus(RecognizerStatus.Kind.INITIALIZING);
            notifyError("“Manga OCR” is taking too long to answer. Restarting");
          }
          default -> throw new IllegalStateException("Unhandled recognizer restart reason");
        }
      }
      case RecognizerEvent.DebugImageSubmitted e ->
        server.send(new DebugImageOutMessage(ImageOps.convertToBase64(e.image())));
      default -> throw new IllegalStateException("Unhandled recognizer event");
    }
  }

  private void updateAndSendRecognizerStatus(RecognizerStatus.Kind statusKind) {
    status.updateRecognizerStatus(statusKind);
    sendStatus(ProgramStatusOutMessage.RecognizerStatus.class);
  }

  private void recognizeRegion(Rectangle region, boolean autoNarrow) {
    LOG.debug("Handling region recognition request ({})", region);
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(
      region,
      TextOrientation.HORIZONTAL,
      /* autoBlockHeuristic */ autoNarrow ? AutoBlockHeuristic.GAME_TEXTBOX : null
    );
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeRegion(
    Rectangle region,
    TextOrientation textOrientation,
    AutoBlockHeuristic autoBlockHeuristic
  ) {
    var screenshotRes = platform.takeAreaScreenshot(region);
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    if (autoBlockHeuristic != null) {
      doRecognizeAutoBlockImageProvided(screenshotRes.get(), textOrientation, autoBlockHeuristic);
    } else {
      recognizeBox(screenshotRes.get(), textOrientation);
    }
  }

  private void recognizeManualBlockDefault() {
    recognizeManualBlock(TextOrientation.UNKNOWN);
  }

  private void recognizeManualBlock(TextOrientation textOrientation) {
    LOG.debug("Handling manual block recognition request");
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var areaRes = platform.getUserSelectedArea();
    if (areaRes.isErr()) {
      var errorNotification = switch (areaRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not get user screen area selection";
      };
      recognitionAbandon(errorNotification, areaRes.err());
      return;
    }

    updateAndSendRecognizerStatus(RecognizerStatus.Kind.PROCESSING);
    doRecognizeRegion(areaRes.get(), textOrientation, /* heuristic */ null);
    // doRecognizeRegion(areaRes.get(), /* heuristic */ AutoBlockHeuristic.GAME_TEXTBOX); // DEV
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.IDLE);
  }

  private void recognizeAutoBlockDefault() {
    recognizeAutoBlock(TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_FULL);
  }

  private void recognizeAutoBlockColumnDefault() {
    recognizeAutoBlock(TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_SINGLE_COLUMN);
  }

  @SuppressWarnings("SameParameterValue")
  private void recognizeAutoBlock(TextOrientation textOrientation, AutoBlockHeuristic mode) {
    LOG.debug("Handling auto block recognition request (mode = {})", mode);
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.AWAITING_USER_INPUT);

    var selectionRes = platform.getUserSelectedPoint();
    if (selectionRes.isErr()) {
      var errorNotification = switch (selectionRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could get user screen point selection";
      };
      recognitionAbandon(errorNotification, selectionRes.err());
      return;
    }

    updateAndSendRecognizerStatus(RecognizerStatus.Kind.PROCESSING);

    var point = selectionRes.get();
    var screenshotRes = platform.takeAreaScreenshot(
      Rectangle.around(point, Recognizer.AUTO_BLOCK_AREA_SIZE)
    );
    if (screenshotRes.isErr()) {
      var errorNotification = switch (screenshotRes.err()) {
        case SELECTION_CANCELLED -> null;
        default                  -> "Could not take a screenshot";
      };
      recognitionAbandon(errorNotification, screenshotRes.err());
      return;
    }

    doRecognizeAutoBlockImageProvided(screenshotRes.get(), textOrientation, mode);
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.IDLE);
  }

  @SuppressWarnings("SameParameterValue")
  private void recognizeAutoBlockImageProvided(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic mode
  ) {
    LOG.debug("Handling auto block image recognition request (mode = {})", mode);
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.PROCESSING);
    doRecognizeAutoBlockImageProvided(img, textOrientation, mode);
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.IDLE);
  }

  private void doRecognizeAutoBlockImageProvided(
    BufferedImage img, TextOrientation textOrientation, AutoBlockHeuristic heuristic
  ) {
    var maybeBlockImg = recognizer.autoNarrowToTextBlock(img, heuristic);
    if (maybeBlockImg.isEmpty()) {
      var msg = "Text block detection has failed";
      notifyError(msg);
      LOG.info(msg);
      return;
    }
    recognizeBox(maybeBlockImg.get(), textOrientation);
  }

  private void recognizeBox(BufferedImage img, TextOrientation textOrientation) {
    var recognitionRes = recognizer.recognizeBox(img, textOrientation);
    if (recognitionRes.isErr()) {
      var errorNotification = switch (recognitionRes.err()) {
        case SELECTION_CANCELLED -> null;
        case INPUT_TOO_SMALL     -> "Input image is too small";
        case ZERO_VARIANTS       -> "Could not recognize any text";
        default -> "Box recognition has failed.\nCheck control window or console for errors";
      };
      recognitionAbandon(errorNotification, recognitionRes.err());
      return;
    }
    server.send(new ChunkVariantsOutMessage(
      recognitionRes.get().chunkVariants().getPostprocessedChunks(config.chunk().correct())
    ));
  }

  private void recognitionAbandon(String errorNotification, RecognitionOpError errorToLog) {
    if (errorNotification != null) {
      notifyError(errorNotification);
    }
    updateAndSendRecognizerStatus(RecognizerStatus.Kind.IDLE);
    if (errorToLog != null) {
      recognitionLogError(errorToLog);
    }
  }

  private static void recognitionLogError(RecognitionOpError reason) {
    switch (reason) { // NOPMD - misidentifies as non-exhaustive
      case OCR_UNAVAILABLE ->
        LOG.error("No OCR engine is available for use");
      case SCREENSHOT_API_COMMUNICATION_FAILED ->
        LOG.error("Failed to communicate with the screenshot API");
      case SELECTION_CANCELLED ->
        LOG.debug("Screen area selection was cancelled by user");
      case SELECTION_FAILED ->
        LOG.error("Failed to perform screen area selection");
      case SCREENSHOT_FAILED ->
        LOG.error("Failed to take a screenshot");
      case INPUT_TOO_SMALL ->
        LOG.error("Input image is too small");
      case OCR_ERROR ->
        LOG.error("OCR failed abnormally");
      case ZERO_VARIANTS ->
        LOG.debug("Could not recognize text");
    }
  }

  private void notifyError(String content) {
    server.notify(NotificationKind.ERROR, content);
  }

  private void handleServerEvent(ServerEvent event) {
    switch (event) {
      case ServerEvent.Started ignored -> {
        if (config.launchBrowser()) {
          platform.openURL("http://localhost:%d".formatted(config.server().port()));
          LOG.info("Navigated to web UI URL in web browser");
        }
      }
      case ServerEvent.ClientConnected ignored -> {
        LOG.info("Client connected");
        server.send(new ConfigOutMessage(config));
        sendStatus(ProgramStatusOutMessage.Full.class);
      }
      case ServerEvent.CommandReceived e ->
        handleCommand(e.command(), CommandSource.API);
      case ServerEvent.MessageReceived e ->
        handleInMessage(e.message());
      case ServerEvent.AboutToSendMessage e -> {
        var msg = e.message();
        if (msg instanceof ChunkVariantsOutMessage cvMsg) {
          status.getCharacterCounter().register(cvMsg.getVariants().get(0));
          sendStatus(ProgramStatusOutMessage.CharacterCounter.class);
        }
      }
      default -> throw new IllegalStateException("Unhandled server event");
    }
  }

  private void handleDBusEvent(DBusEvent event) {
    switch (event) {
      case DBusEvent.CommandReceived e -> handleCommand(e.command(), CommandSource.API);
      default -> throw new IllegalStateException("Unhandled dbus event");
    }
  }

  private void handleShowChunkPostCheckpoint(IncomingChunkText chunk) {
    if (ChunkFilter.shouldReject(chunk.text())) {
      LOG.info("Rejected a chunk because it matched a filter pattern");
      LOG.debug("Rejected chunk: {}", chunk.text()); // NOPMD
      return;
    }
    var post = ChunkVariants.singleFromString(chunk.text())
      .getPostprocessedChunks(config.chunk().correct());
    server.send(new ChunkVariantsOutMessage(post, chunk.playbackTimeS()));
  }

  private void handleInMessage(InMessage message) {
    switch (message) {
      case InMessage.Command msg -> handleCommand(msg.incomingCommand(), CommandSource.CLIENT);
      case InMessage.Request msg -> handleRequest(msg.request());
      default -> throw new IllegalStateException("Unhandled server message");
    }
  }

  private void handleCommand(IncomingCommand incoming, CommandSource source) {
    try {
      doHandleCommand(incoming, source);
    } catch (Exception e) {
      LOG.error("Unhandled exception while handling a command. See stderr for the stack trace");
      e.printStackTrace();
    }
  }

  private void doHandleCommand(IncomingCommand incoming, CommandSource source) {
    var cmdParseRes = Command.fromIncoming(incoming);
    if (cmdParseRes.isErr()) {
      LOG.warn("Error parsing command: {}", cmdParseRes.err()); // NOPMD
      return;
    }
    var command = cmdParseRes.get();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Handling command: {}", command.getClass());
    }
    switch (command) {
      case Command.OCR cmd -> {
        var refuseMsg = switch (status.getRecognizerStatus().getKind()) {
          case UNAVAILABLE ->
            "Text recognition is not available in this session";
          case INITIALIZING ->
            "Text recognizer is still initializing. Please wait";
          case AWAITING_USER_INPUT, PROCESSING ->
            "Another text recognition operation is already in progress";
          default -> {
            var noGlobalOCR = platform.getUnsupportedFeatures()
              .contains(PlatformDependentFeature.GLOBAL_OCR);
            if (noGlobalOCR && cmd.isGlobalOCRCommand()) {
              yield "The current platform does not support global OCR commands";
            }
            yield null;
          }
        };
        if (refuseMsg != null) {
          server.send(new NotificationOutMessage(NotificationKind.INFO, refuseMsg));
          LOG.warn("Refused OCR command. Reason: {}", refuseMsg);
          return;
        }
        switch (cmd) {
          case Command.OCR.ManualBlock ignored ->
            recognizeManualBlockDefault();
          case Command.OCR.ManualBlockVertical ignored ->
            recognizeManualBlock(TextOrientation.VERTICAL);
          case Command.OCR.ManualBlockHorizontal ignored ->
            recognizeManualBlock(TextOrientation.HORIZONTAL);
          case Command.OCR.AutoBlock ignored ->
            recognizeAutoBlockDefault();
          case Command.OCR.AutoColumn ignored ->
            recognizeAutoBlockColumnDefault();
          case Command.OCR.Region cm ->
            recognizeRegion(cm.region(), cm.autoNarrow());
          case Command.OCR.Image cm ->
            handleOCRImageCommand(cm.bytesB64(), cm.size());
          default -> throw new IllegalStateException("Unhandled command type");
        }
      }

      case Command.Player cmd -> {
        var mpvCmd = switch (cmd) {
          case Command.Player.PlayPause ignored    -> MPVCommand.PLAYPAUSE;
          case Command.Player.SeekBack ignored     -> MPVCommand.SEEK_BACK;
          case Command.Player.SeekForward ignored  -> MPVCommand.SEEK_FORWARD;
          case Command.Player.SeekStartSub ignored -> MPVCommand.SEEK_START_SUB;
          default -> throw new IllegalStateException("Unhandled command type");
        };
        mpvController.sendCommand(mpvCmd);
      }

      case Command.CharacterCounter cmd -> {
        switch (cmd) {
          case Command.CharacterCounter.ToggleFreeze ignored ->
            status.getCharacterCounter().toggleFreeze();
          case Command.CharacterCounter.Reset ignored ->
            status.getCharacterCounter().reset();
          default ->
            throw new IllegalStateException("Unhandled command type");
        }
        sendStatus(ProgramStatusOutMessage.CharacterCounter.class);
      }

      case Command.SessionTimer cmd -> {
        switch (cmd) {
          case Command.SessionTimer.TogglePause ignored ->
            status.getSessionTimer().togglePause();
          case Command.SessionTimer.Reset ignored ->
            status.getSessionTimer().reset();
          default ->
            throw new IllegalStateException("Unhandled command type");
        }
        sendStatus(ProgramStatusOutMessage.SessionTimer.class);
      }

      case Command.Chunk.Show cmd ->
        chunkCheckpoint.register(cmd.chunk());
      case Command.Chunk.ShowTranslation cmd ->
        server.send(new ChunkTranslationOutMessage(
          TextProcessor.correctForm(cmd.translation().translation()),
          cmd.translation().playbackTimeS()
        ));

      case Command.Other.Custom cmd -> {
        if (source == CommandSource.API) {
          LOG.warn("Command 'other_custom' is inaccessible through the API");
        } else {
          runCustomCommand(cmd.command());
        }
      }

      default ->
        throw new IllegalStateException("Unhandled command type");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Finished handling command: {}", command.getClass());
    }
  }

  private void handleOCRImageCommand(String bytesB64, Dimension size) {
    var bytes = Base64.getDecoder().decode(bytesB64);
    var img = ImageOps.arrayToBufferedImage(bytes, size.width(), size.height());
    recognizeAutoBlockImageProvided(img, TextOrientation.VERTICAL, AutoBlockHeuristic.MANGA_FULL);
  }

  private void runCustomCommand(String[] command) {
    if (ProcessHelper.run(command).didFailOrError()) {
      notifyError("Custom command has failed");
    }
  }

  private void handleRequest(Request request) {
    switch (request.body()) {
      case Request.Body.AddFurigana body ->
        server.send(
          new ResponseOutMessage(
            request.timestamp(),
            new ChunkWithFuriganaOutMessage(textProcessor.addFurigana(body.text()))
          )
        );
      default -> throw new IllegalStateException("Unhandled request type");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Handled request: {}", request.body().getClass());
    }
  }

  private void sendStatus(Class<? extends ProgramStatusOutMessage> clazz) {
    try {
      server.send(clazz.getConstructor(ProgramStatus.class).newInstance(status));
    } catch (
      IllegalAccessException
      | InstantiationException
      | NoSuchMethodException
      | InvocationTargetException e
    ) {
      throw new RuntimeException("Exception while instantiating Program Status message", e);
    }
  }

  private void processConfig(PreconfigArgs preconfigArgs) {
    status = new ProgramStatus(
      preconfigArgs.debug(),
      preconfigArgs.profileName(),
      config.lookup().targets(),
      SessionTimer.startingNow(),
      new CharacterCounter(),
      RecognizerStatus.Kind.INITIALIZING,
      PlayerStatus.DISCONNECTED
    );

    if (platform instanceof GlobalKeybindingProvider keybindingProvider) {
      setupGlobalKeybindings(keybindingProvider, config.keybindings().global());
    }
  }

  private void setupGlobalKeybindings(
    GlobalKeybindingProvider provider,
    Config.Keybindings.Global keybindings
  ) {
    var registrationState = new Object() {
      @SuppressWarnings("PackageVisibleField") boolean registeredAtLeastOne;
    };
    Map.<String, Runnable>of(
      keybindings.ocr().manualBlock(), this::recognizeManualBlockDefault,
      keybindings.ocr().autoBlock(), this::recognizeAutoBlockDefault
    )
      .forEach((binding, handler) -> {
        if (binding != null) {
          provider.registerKeybinding(binding, handler);
          LOG.debug("Registered global keybinding: {}", binding);
          registrationState.registeredAtLeastOne = true;
        }
      });
    if (registrationState.registeredAtLeastOne) {
      LOG.info("Registered global keybindings");
    }
  }

  private record PreconfigArgs(boolean debug, String profileName) {}

  private static PreconfigArgs processPreconfigArgs(Map<String, String> args) {
    var debug = args.get("debug");
    var statusDebug = !isArgValueFalsey(debug);
    if (statusDebug) {
      var loggingExtent =
        "all".equalsIgnoreCase(debug)
        ? DebugLoggingExtent.EVERYTHING
        : DebugLoggingExtent.APP;
      enableDebugLogging(loggingExtent);
    }
    return new PreconfigArgs(statusDebug, args.get("profile"));
  }

  private static boolean isArgValueFalsey(String value) {
    return value == null || "false".equalsIgnoreCase(value) || "0".equalsIgnoreCase(value);
  }

  private enum DebugLoggingExtent { APP, EVERYTHING }

  private static void enableDebugLogging(DebugLoggingExtent extent) {
    var loggerCtx = (LoggerContext) LogManager.getContext(false);
    var loggerConfig = loggerCtx.getConfiguration();
    loggerConfig.getLoggerConfig("io.github.kamitejp").setLevel(Level.DEBUG);
    if (extent == DebugLoggingExtent.EVERYTHING) {
      loggerConfig.getRootLogger().setLevel(Level.DEBUG);
    }
    loggerCtx.updateLoggers();
  }
}
