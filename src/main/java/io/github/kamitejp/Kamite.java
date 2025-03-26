package io.github.kamitejp;

import static java.util.stream.Collectors.joining;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import io.github.kamitejp.api.Command;
import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.api.Request;
import io.github.kamitejp.chunk.ChunkCheckpoint;
import io.github.kamitejp.chunk.ChunkCorrectionPolicy;
import io.github.kamitejp.chunk.ChunkEnhancements;
import io.github.kamitejp.chunk.ChunkFilter;
import io.github.kamitejp.chunk.ChunkLogger;
import io.github.kamitejp.chunk.ChunkLoggerInitializationException;
import io.github.kamitejp.chunk.ChunkTransformer;
import io.github.kamitejp.chunk.ChunkTranslationDestination;
import io.github.kamitejp.chunk.IncomingChunkText;
import io.github.kamitejp.chunk.UnprocessedChunkVariants;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.config.ConfigManager;
import io.github.kamitejp.controlgui.ControlGUI;
import io.github.kamitejp.dbus.DBusEvent;
import io.github.kamitejp.event.Event;
import io.github.kamitejp.event.EventHandler;
import io.github.kamitejp.event.EventManager;
import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.meta.BuildInfo;
import io.github.kamitejp.meta.Releases;
import io.github.kamitejp.meta.Version;
import io.github.kamitejp.platform.GlobalKeybindingProvider;
import io.github.kamitejp.platform.InvalidKeyStrokeException;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformDependentFeature;
import io.github.kamitejp.platform.PlatformInitializationException;
import io.github.kamitejp.platform.PlatformOCRInfrastructureInitializationException;
import io.github.kamitejp.platform.agent.AgentClient;
import io.github.kamitejp.platform.linux.LinuxPlatform;
import io.github.kamitejp.platform.mpv.MPVCommand;
import io.github.kamitejp.platform.mpv.MPVController;
import io.github.kamitejp.platform.mpv.Subtitle;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.recognition.AutoBlockHeuristic;
import io.github.kamitejp.recognition.OCRDirectoryWatcher;
import io.github.kamitejp.recognition.OCRDirectoryWatcherCreationException;
import io.github.kamitejp.recognition.PointSelectionMode;
import io.github.kamitejp.recognition.RecognitionConductor;
import io.github.kamitejp.recognition.RecognizerEvent;
import io.github.kamitejp.recognition.RecognizerStatus;
import io.github.kamitejp.server.InMessage;
import io.github.kamitejp.server.Server;
import io.github.kamitejp.server.ServerEvent;
import io.github.kamitejp.server.ServerStartException;
import io.github.kamitejp.server.UserNotificationKind;
import io.github.kamitejp.server.outmessage.ChunkEnhancementsOutMessage;
import io.github.kamitejp.server.outmessage.ChunkTranslationOutMessage;
import io.github.kamitejp.server.outmessage.ChunkVariantsOutMessage;
import io.github.kamitejp.server.outmessage.ConfigOutMessage;
import io.github.kamitejp.server.outmessage.DebugImageOutMessage;
import io.github.kamitejp.server.outmessage.LookupRequestOutMessage;
import io.github.kamitejp.server.outmessage.OutMessage;
import io.github.kamitejp.server.outmessage.ProgramStatusOutMessage;
import io.github.kamitejp.server.outmessage.ResponseOutMessage;
import io.github.kamitejp.server.outmessage.UserNotificationOutMessage;
import io.github.kamitejp.status.CharacterCounter;
import io.github.kamitejp.status.PlayerStatus;
import io.github.kamitejp.status.ProgramStatus;
import io.github.kamitejp.status.SessionTimer;
import io.github.kamitejp.textprocessing.TextProcessor;
import io.github.kamitejp.textprocessing.kuromoji.KuromojiAdapter;
import io.github.kamitejp.universalfeature.UnavailableAutoFurigana;
import io.github.kamitejp.universalfeature.UnavailableUniversalFeature;
import io.github.kamitejp.util.Executor;
import io.github.kamitejp.util.Result;

public class Kamite {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
  public static final String APP_NAME_DISPLAY = "Kamite";

  // QUAL: Make neater
  public static final Map<String, String> PRECONFIG_ARGS = Map.of(
    "debug", "debug",
    "profile", "profile",
    "regionHelper", "regionHelper",
    "countChars", "countChars"
  );

  private static final List<String> DEFAULT_SUBSCRIBED_EVENTS = List.of("chunk-add");

  private Platform platform;
  private Server server;
  private ConfigManager configManager;
  private Config config;
  private RecognitionConductor recognitionConductor;
  private OCRDirectoryWatcher ocrDirectoryWatcher;
  private TextProcessor textProcessor;
  private MPVController mpvController;
  private AgentClient agentClient;
  private ChunkCheckpoint chunkCheckpoint;
  private ChunkFilter chunkFilter;
  private ChunkTransformer chunkTransformer;
  private ChunkLogger chunkLogger;
  private EventManager eventManager;
  private ProgramStatus status;

  public void run(Map<String,String> args, BuildInfo buildInfo) {
    LOG.info("Starting {} (version {})", () -> APP_NAME_DISPLAY, buildInfo::getVersion);

    var preconfigArgs = processPreconfigArgs(args);

    if (preconfigArgs.countChars()) {
      runCountCharsMode();
      System.exit(0);
      return;
    }

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
    LOG.info("Initialized support for platform {}", () -> platform.getClass().getSimpleName());

    if (preconfigArgs.regionHelper()) {
      Runtime.getRuntime().addShutdownHook(new Thread(platform::destroy));
      runRegionHelperMode();
      System.exit(0);
      return;
    }

    // Load config
    var maybeConfigDirPath = platform.getConfigDirPath();
    if (maybeConfigDirPath.isEmpty()) {
      createControlGUIAndShowFatalError("Failed to get platform-specific config path");
      return;
    }
    configManager = new ConfigManager(this::handleConfigReload);
    var configReadRes =
      configManager.read(maybeConfigDirPath.get(), preconfigArgs.profileNames(), args);
    if (configReadRes.isErr()) {
      createControlGUIAndShowFatalError("Failed to read config", configReadRes.err());
      return;
    }
    var configReadSuccess = configReadRes.get();
    config = configReadSuccess.config();

    // Detect unavailable platform-independent features
    var unavailableUniversalFeatures = new ArrayList<UnavailableUniversalFeature>(1);
    var kuromojiAdapter = new KuromojiAdapter(platform);
    if (!kuromojiAdapter.isKuromojiAvailable()) {
      unavailableUniversalFeatures.add(
        new UnavailableAutoFurigana(UnavailableAutoFurigana.Reason.KUROMOJI_UNAVAILABLE)
      );
      if (config.chunk().furigana().enable()) {
        LOG.warn(
          "`chunk.furigana.enable` is turned on, but a library needed for generating furigana is"
          + " not available"
        );
      }
    }

    status = new ProgramStatus(
      preconfigArgs.debug(),
      preconfigArgs.profileNames(),
      config.sessionTimer().startPaused()
        ? SessionTimer.pausedAtZero()
        : SessionTimer.startingNow(),
      new CharacterCounter(),
      unavailableUniversalFeatures,
      RecognizerStatus.Kind.INITIALIZING,
      PlayerStatus.DISCONNECTED,
      DEFAULT_SUBSCRIBED_EVENTS
    );

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

    if (config.controlWindow()) {
      createControlGUI();
      LOG.info(
        "Created control window ({} version {})",
        () -> APP_NAME_DISPLAY,
        buildInfo::getVersion
      );
    }

    // Unsupported platform features message deferred until control GUI potentially present
    var unsupportedFeatures = platform.getUnsupportedFeatures();
    if (!unsupportedFeatures.isEmpty()) {
      LOG.warn(
        "The current platform does not support the following features:\n{}",
        () -> unsupportedFeatures.stream()
          .map(f -> "– %s (%s)".formatted(f.getDisplayName(), f.getDescription()))
          .collect(joining("\n"))
      );
    }

    // Config load message deferred until control GUI potentially present
    if (!configReadSuccess.loadedProfileNames().isEmpty()) {
      LOG.info(
        "Loaded config profiles: {}",
        () -> String.join(", ", configReadSuccess.loadedProfileNames())
      );
    }

    // Init DBus communication
    // QUAL: Move to LinuxPlatform?
    if (platform instanceof LinuxPlatform linuxPlatform) {
      linuxPlatform.getDBusClient().ifPresent((client) -> {
        client.onEvent(this::handleDBusEvent);
        LOG.debug("Connected DBus event handler");
      });
    }

    chunkCheckpoint = new ChunkCheckpoint(
      config.chunk().throttleMS(),
      /* onAllowedThrough */ this::showChunkPostCheckpoint
    );

    var chunkConfig = config.chunk();
    initOrDiscardChunkFilter(chunkConfig.filter());
    initOrDiscardChunkTransformer(chunkConfig.transforms());

    textProcessor = new TextProcessor(kuromojiAdapter);

    mpvController = MPVController.create(
      platform, this::handlePlayerStatusUpdate, this::handlePlayerSubtitle
    );

    initOrDiscardAgentClient(config.integrations().agent());

    recognitionConductor = new RecognitionConductor(
      platform,
      status,
      /* recognizerEventCb */               this::handleRecognizerEvent,
      /* chunkVariantsCb */                 this::handleChunkVariants,
      /* notifyUserOfErrorFn */             this::notifyUserOfError,
      /* updateAndSendRecognizerStatusFn */ this::updateAndSendRecognizerStatus
    );

    // NOTE: setupGlobalKeybindings() depends on recognitionConductor being present (but this can be
    //       changed)
    if (platform.supports(PlatformDependentFeature.GLOBAL_KEYBINDINGS)) {
      if (platform instanceof GlobalKeybindingProvider keybindingProvider) {
        setupGlobalKeybindings(keybindingProvider);
      } else {
        LOG.warn(
          "Platform reported supporting global keybindings, yet it does not implement the required"
          + " interface. Global keybindings will be unavailable"
        );
      }
    }

    if (config.chunk().log().dir() != null) {
      try {
        chunkLogger = new ChunkLogger(config.chunk().log().dir());
      } catch (ChunkLoggerInitializationException e) {
        LOG.error("Could not initialize chunk logger:", e);
      }
    }

    // Defer to here to have chunk logging initialized before recognizer, so that chunks received
    // during a long manga-ocr initialization aren't missing from the log.
    Executor.get().execute(() -> recognitionConductor.initRecognizer(config));

    // NOTE: OCRDirectoryWatcher constructor depends on recognitionConductor being present (but this
    //       can be changed)
    var ocrWatchDir = config.ocr().watchDir();
    if (ocrWatchDir != null) {
      try {
        ocrDirectoryWatcher = new OCRDirectoryWatcher(
          ocrWatchDir,
          /* recognizeImageFn */ recognitionConductor::recognizeGivenImage
        );
      } catch (OCRDirectoryWatcherCreationException e) {
        LOG.error("Failed to create OCR directory watcher: {}", e::toString);
      }
    }

    eventManager = new EventManager(
      /* handlerDefinitions */ config.events().handlers(),
      /* commandCb */ (IncomingCommand command) ->
        handleCommand(command, CommandSource.EVENT_HANDLER),
      /* handledEventsChangedCb */ (List<String> handledEvents) -> {
        status.setSubscribedEvents(handledEvents);
        sendStatus(ProgramStatusOutMessage.SubscribedEvents.class);
      }
    );
    if (chunkLogger != null) {
      eventManager.registerEventHandler(
        Event.ChunkAdd.class,
        EventHandler.internalOfConsumer(
          event -> chunkLogger.log(((Event.ChunkAdd) event).chunkText())
        )
      );
    }

    if (config.update().check()) {
      Executor.get().execute(() -> notifyIfNewerVersionAvailable(buildInfo));
    }

    Runtime.getRuntime().addShutdownHook(new Thread(this::destroy));
  }

  private void destroy() {
    platform.destroy();
    configManager.destroy();
    server.destroy();
    if (ocrDirectoryWatcher != null) {
      ocrDirectoryWatcher.destroy();
    }
    if (recognitionConductor != null) {
      recognitionConductor.destroy();
    }
    mpvController.destroy();
    if (agentClient != null) {
      agentClient.destroy();
    }
    if (chunkLogger != null) {
      chunkLogger.finalizeLog();
    }
    Executor.destroy();
  }

  private void notifyIfNewerVersionAvailable(BuildInfo buildInfo) {
    if (
      buildInfo.getVersion() instanceof Version.Release ver
      && Releases.checkNewAvailable(ver) == Releases.NewCheckResult.AVAILABLE
    ) {
      LOG.info(
        "There is a newer version of {} available at {}",
        APP_NAME_DISPLAY,
        Releases.PAGE_URL
      );
    }
  }

  private void runRegionHelperMode() {
    if (platform.getUnsupportedFeatures().contains(PlatformDependentFeature.GLOBAL_OCR)) {
      LOG.error(
        "The current platform does not support Global OCR."
        + "The Region Helper mode is not available"
      );
      return;
    }
    try {
      platform.initOCRInfrastructure();
    } catch (PlatformOCRInfrastructureInitializationException e) {
      LOG.error("Could not init platform OCR for Region Helper Mode:", e);
    }

    System.out.println(
      "\nStarted in Region Helper mode. Select to print region, cancel selection (Escape) to exit\n"
    );

    while (true) {
      var areaRes = platform.getUserSelectedArea();
      if (areaRes.isErr()) {
        var exitMsg = switch (areaRes.err()) {
          case SELECTION_CANCELLED -> "Selection has been cancelled";
          default -> "There has been an error getting area selection (%s)".formatted(areaRes.err());
        };
        System.out.printf("%s. Exiting%n", exitMsg);
        return;
      }
      var area = areaRes.get();
      System.out.printf(
        "    x = %s\n    y = %s\n    width = %s\n    height = %s\n%n",
        area.getLeft(),
        area.getTop(),
        area.getWidth(),
        area.getHeight()
      );
    }
  }

  private void runCountCharsMode() {
    var scanner = new Scanner(System.in);
    var count = 0;
    while (scanner.hasNext()) {
      count += CharacterCounter.count(scanner.nextLine());
    }
    scanner.close();
    System.out.printf("\nCharacters: %d%n", count);
  }

  private void createControlGUI() {
    new ControlGUI(platform);
  }

  private void createControlGUIAndShowFatalError(String message) {
    createControlGUIAndShowFatalError(message, null);
  }

  private void createControlGUIAndShowFatalError(String message, String details) {
    createControlGUI();
    showFatalError(message, details);
  }

  private static void showFatalError(String message, String details) {
    var detailsPart = details == null ? "" : " Details: %s".formatted(details);
    LOG.error("{}. The program will not continue.{}", message, detailsPart);
  }

  private void initOrDiscardChunkFilter(Config.Chunk.Filter filterConfig) {
    var shouldInit = filterConfig != null
      && filterConfig.rejectPatterns() != null
      && !filterConfig.rejectPatterns().isEmpty();
    chunkFilter = shouldInit
      ? new ChunkFilter(filterConfig.rejectPatterns())
      : null;
  }

  private void initOrDiscardChunkTransformer(List<Config.Chunk.Transform> transformsConfig) {
    var shouldInit = transformsConfig != null && !transformsConfig.isEmpty();
    chunkTransformer = shouldInit
      ? new ChunkTransformer(transformsConfig)
      : null;
  }

  private void initOrDiscardAgentClient(Config.Integrations.Agent agentConfig) {
    if (agentClient != null) {
      agentClient.destroy();
    }
    agentClient = agentConfig.enable()
      ? new AgentClient(
          agentConfig.host(),
          /* connectCb */ () ->
            notifyUserOfInfo("Connected to Agent"),
          /* disconnectCb */ () ->
            notifyUserOfInfo("Disconnected from Agent"),
          /* chunkCb */ (String chunk) ->
            chunkCheckpoint.register(IncomingChunkText.of(chunk)),
          /* chunkTranslationCb */ (String chunkTranslation) ->
            showChunkTranslation(chunkTranslation)
        )
      : null;
  }

  private void handleConfigReload(Result<Config, String> configReloadRes) {
    if (configReloadRes.isErr()) {
      LOG.error("Could not read new config: {}", configReloadRes.err());
      notifyUserOfError("Could not read new config");
      return;
    }
    var config = configReloadRes.get();

    var chunkConfig = config.chunk();
    var oldChunkConfig = this.config.chunk();
    if (!Objects.equals(chunkConfig.filter(), oldChunkConfig.filter())) {
      initOrDiscardChunkFilter(chunkConfig.filter());
    }
    if (!Objects.equals(chunkConfig.transforms(), oldChunkConfig.transforms())) {
      initOrDiscardChunkTransformer(chunkConfig.transforms());
    }

    var eventsConfig = config.events();
    var oldEventsConfig = this.config.events();
    if (!Objects.equals(eventsConfig.handlers(), oldEventsConfig.handlers())) {
      eventManager.setUserEventHandlers(eventsConfig.handlers());
    }

    var integrationsConfig = config.integrations();
    var oldIntegrationsConfig = this.config.integrations();
    if (!Objects.equals(integrationsConfig.agent(), oldIntegrationsConfig.agent())) {
      initOrDiscardAgentClient(integrationsConfig.agent());
    }

    var msg = "Reloaded config and possibly applied changes";
    LOG.info(msg);
    notifyUserOfInfo(msg);

    this.config = config;
    server.send(new ConfigOutMessage(config));
  }

  private void handlePlayerStatusUpdate(PlayerStatus newStatus) {
    status.setPlayerStatus(newStatus);
    sendStatus(ProgramStatusOutMessage.PlayerStatus.class);
  }

  private void handlePlayerSubtitle(Subtitle subtitle) {
    switch (subtitle.kind()) { // NOPMD - misidentifies as non-exhaustive
      case PRIMARY   -> showChunkPostCheckpoint(subtitle.text(), subtitle.startTimeS());
      case SECONDARY -> showChunkTranslation(
        subtitle.text(),
        ChunkTranslationDestination.LATEST,
        subtitle.startTimeS()
      );
    }
  }

  private void handleChunkVariants(UnprocessedChunkVariants variants) {
    var processedChunks = variants.process(
      ChunkCorrectionPolicy.fromChunkConfig(config.chunk()),
      chunkTransformer
    );
    if (!processedChunks.isEmpty()) {
      server.send(new ChunkVariantsOutMessage(processedChunks));
    }
  }

  private void handleRecognizerEvent(RecognizerEvent event) {
    switch (event) {
      case RecognizerEvent.Initialized e -> {
        status.updateRecognizerStatus(RecognizerStatus.Kind.IDLE, e.availableCommands());
        sendStatus(ProgramStatusOutMessage.RecognizerStatus.class);
      }
      case RecognizerEvent.MangaOCRStartedDownloadingModel _ ->
        notifyUserOfInfo("\"Manga OCR\" is downloading OCR model. This might take a while…");
      case RecognizerEvent.Crashed _ -> {
        LOG.info("Recognizer has crashed and will not be restarted");
        notifyUserOfError("Recognizer has crashed. Text recognition will be unavailable");
        updateAndSendRecognizerStatus(RecognizerStatus.Kind.UNAVAILABLE);
      }
      case RecognizerEvent.Restarting e -> {
        switch (e.reason()) {
          case MANGA_OCR_TIMED_OUT_AND_RESTARTING -> {
            updateAndSendRecognizerStatus(RecognizerStatus.Kind.INITIALIZING);
            notifyUserOfError("\"Manga OCR\" is taking too long to answer. Restarting");
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

  private void notifyUserOfInfo(String content) {
    server.notifyUser(UserNotificationKind.INFO, content);
  }

  private void notifyUserOfError(String content) {
    server.notifyUser(UserNotificationKind.ERROR, content);
  }

  private void handleServerEvent(ServerEvent event) {
    switch (event) {
      case ServerEvent.Started _ -> {
        if (config.launchBrowser()) {
          platform.openURL("http://localhost:%d".formatted(config.server().port()));
          LOG.info("Navigated to web UI URL in web browser");
        }
      }
      case ServerEvent.ClientConnected _ -> {
        LOG.info("Client connected");
        // NOTE: The order is important so that unavailable settings can be disabled before
        //       the config is applied
        sendStatus(ProgramStatusOutMessage.Full.class);
        server.send(new ConfigOutMessage(config));
      }
      case ServerEvent.CommandReceived e ->
        handleCommand(e.command(), CommandSource.API);
      case ServerEvent.MessageReceived e ->
        handleInMessage(e.message());
      case ServerEvent.AboutToSendMessage e -> {
        if (e.message() instanceof ChunkVariantsOutMessage cvMsg) {
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

  private void showChunkPostCheckpoint(IncomingChunkText chunk) {
    showChunkPostCheckpoint(chunk.text(), chunk.playbackTimeS());
  }

  private void showChunkPostCheckpoint(String text, Double playbackTimeS) {
    if (chunkFilter != null && chunkFilter.shouldReject(text)) {
      LOG.info("Rejected a chunk because it matched a filter pattern");
      LOG.debug("Rejected chunk: {}", text);
      return;
    }
    var processedChunks = UnprocessedChunkVariants.singleFromString(text)
      .process(
        ChunkCorrectionPolicy.fromChunkConfig(config.chunk()),
        chunkTransformer
      );
    if (!processedChunks.isEmpty()) {
      server.send(new ChunkVariantsOutMessage(processedChunks, playbackTimeS));
    }
  }

  private void showChunkTranslation(String translation) {
    showChunkTranslation(translation, ChunkTranslationDestination.LATEST, null);
  }

  private void showChunkTranslation(
    String translation,
    ChunkTranslationDestination destination,
    Double playbackTimeS
  ) {
    server.send(new ChunkTranslationOutMessage(
      TextProcessor.correctForm(translation),
      destination,
      playbackTimeS
    ));
  }

  private void handleInMessage(InMessage message) {
    switch (message) { // NOPMD - misidentifies as non-exhaustive
      case InMessage.Command msg
        -> handleCommand(msg.incomingCommand(), CommandSource.CLIENT);
      case InMessage.Request msg
        -> handleRequest(msg.request());
      case InMessage.EventNotification msg
        -> handleEventNotification(msg.event());
    }
  }

  private void handleCommand(IncomingCommand incoming, CommandSource source) {
    try {
      doHandleCommand(incoming, source);
    } catch (RuntimeException e) {
      LOG.error("Unhandled exception while handling a command. See stderr for the stack trace");
      e.printStackTrace();
    }
  }

  private void doHandleCommand(IncomingCommand incoming, CommandSource source) {
    var cmdParseRes = Command.fromIncoming(incoming);
    if (cmdParseRes.isErr()) {
      LOG.warn("Error parsing command: {}", cmdParseRes::err);
      return;
    }
    var command = cmdParseRes.get();
    LOG.debug("Handling command: {}", command::getClass);
    switch (command) { // NOPMD - misidentifies as non-exhaustive
      case Command.OCR cmd -> {
        var refuseMsg = switch (status.getRecognizerStatus().getKind()) {
          case UNAVAILABLE ->
            "Text recognition is not available in this session";
          case INITIALIZING ->
            "Text recognizer is still initializing. Please wait";
          case AWAITING_USER_INPUT, PROCESSING ->
            "Another text recognition operation is already in progress";
          default -> {
            var noGlobalOCR = !platform.supports(PlatformDependentFeature.GLOBAL_OCR);
            if (noGlobalOCR && cmd.isGlobalOCRCommand()) {
              yield "The current platform does not support global OCR commands";
            }
            yield null;
          }
        };
        if (refuseMsg != null) {
          server.send(new UserNotificationOutMessage(UserNotificationKind.INFO, refuseMsg));
          LOG.warn("Refused OCR command. Reason: {}", refuseMsg);
          return;
        }
        switch (cmd) { // NOPMD - misidentifies as non-exhaustive
          case Command.OCR.ManualBlock _ ->
            recognitionConductor.recognizeManualBlock();
          case Command.OCR.ManualBlockRotated _ ->
            recognitionConductor.recognizeManualBlockRotated();
          case Command.OCR.AutoBlock cm ->
            recognitionConductor.recognizeAutoBlockDefault(cm.mode());
          case Command.OCR.AutoColumn cm ->
            recognitionConductor.recognizeAutoBlockColumnDefault(cm.mode());
          case Command.OCR.Region cm ->
            recognitionConductor.recognizeRegion(cm.region(), cm.autoNarrow());
          case Command.OCR.Image cm ->
            handleOCRImageCommand(cm.bytesB64(), cm.size());
        }
      }

      case Command.Player cmd -> {
        var mpvCmd = switch (cmd) {
          case Command.Player.PlayPause _    -> new MPVCommand.PlayPause();
          case Command.Player.SeekBack _     -> new MPVCommand.Seek(-1);
          case Command.Player.SeekForward _  -> new MPVCommand.Seek(1);
          case Command.Player.SeekStartSub _ -> new MPVCommand.SeekStartSub();
        };
        mpvController.sendCommand(mpvCmd);
      }

      case Command.CharacterCounter cmd -> {
        switch (cmd) { // NOPMD - misidentifies as non-exhaustive
          case Command.CharacterCounter.ToggleFreeze _ ->
            status.getCharacterCounter().toggleFreeze();
          case Command.CharacterCounter.Reset _ -> {
            status.getCharacterCounter().reset();
            notifyUserOfInfo("Character counter has been reset");
          }
        }
        sendStatus(ProgramStatusOutMessage.CharacterCounter.class);
      }

      case Command.SessionTimer cmd -> {
        switch (cmd) { // NOPMD - misidentifies as non-exhaustive
          case Command.SessionTimer.Start _ ->
            status.getSessionTimer().start();
          case Command.SessionTimer.Stop _ ->
            status.getSessionTimer().stop();
          case Command.SessionTimer.Toggle _ ->
            status.getSessionTimer().toggle();
          case Command.SessionTimer.Reset _ -> {
            status.getSessionTimer().reset();
            notifyUserOfInfo("Session timer has been reset");
          }
        }
        sendStatus(ProgramStatusOutMessage.SessionTimer.class);
      }

      case Command.Chunk.Show cmd ->
        chunkCheckpoint.register(cmd.chunk());
      case Command.Chunk.ShowTranslation cmd ->
        showChunkTranslation(
          cmd.translation().translation(),
          cmd.translation().destination(),
          cmd.translation().playbackTimeS()
        );

      case Command.Misc.Custom cmd -> {
        if (source == CommandSource.API) {
          LOG.warn(
            "Tried accessing command `misc_custom` through the API, but this is not allowed"
          );
        } else {
          runCustomCommand(cmd.command());
        }
      }
      case Command.Misc.Lookup cmd ->
        server.send(new LookupRequestOutMessage(cmd.targetSymbol(), cmd.customText()));
    }

    LOG.debug("Finished handling command: {}", command::getClass);
  }

  private void handleOCRImageCommand(String bytesB64, Dimension size) {
    var bytes = Base64.getDecoder().decode(bytesB64);
    var img = ImageOps.arrayToBufferedImage(bytes, size.width(), size.height());
    recognitionConductor.recognizeAutoBlockGivenImage(img, AutoBlockHeuristic.MANGA_FULL);
  }

  private void runCustomCommand(String[] command) {
    if (!ProcessHelper.run(command).didCompleteWithoutError()) {
      notifyUserOfError("Custom command did not run successfully");
    }
  }

  private void handleRequest(Request request) {
    OutMessage response = null;

    switch (request.body()) { // NOPMD - misidentifies as non-exhaustive
      case Request.Body.GetChunkEnhancements body -> {
        ChunkEnhancements enhancements = null;

        switch (body.enhancements().size()) {
          case 0 ->
            LOG.warn("Received an empty chunk enhancements request");
          case 1 -> {
            enhancements = switch (body.enhancements().get(0)) {
              case FURIGANA ->
                textProcessor.addFurigana(body.text())
                  .map(maybeRubies -> ChunkEnhancements.ofFuriganaMaybeRubies(maybeRubies))
                  .orElse(null);
            };
          }
          default ->
            LOG.error("Requested multiple chunk enhancements, which is not yet implemented");
        }

        if (enhancements != null) {
          response = new ChunkEnhancementsOutMessage(enhancements);
        }
      }
    }

    if (response != null) {
      server.send(new ResponseOutMessage(request.timestamp(), response));
    }

    LOG.debug("Handled request: {}", () -> request.body().getClass());
  }

  private void handleEventNotification(Event notification) {
    eventManager.handle(notification);
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

  private final Map<Function<Config.Keybindings.Global, String>, Supplier<Runnable>>
    baseGlobalKeybindings = Map.of(
      (Config.Keybindings.Global keybindings) -> keybindings.ocr().manualBlock(),
      () -> recognitionConductor::recognizeManualBlock,

      (Config.Keybindings.Global keybindings) -> keybindings.ocr().manualBlockRotated(),
      () -> recognitionConductor::recognizeManualBlockRotated,

      (Config.Keybindings.Global keybindings) -> keybindings.ocr().autoBlock(),
      () -> () -> recognitionConductor.recognizeAutoBlockDefault(PointSelectionMode.INSTANT),

      (Config.Keybindings.Global keybindings) -> keybindings.ocr().autoBlockSelect(),
      () -> () -> recognitionConductor.recognizeAutoBlockDefault(PointSelectionMode.SELECT)
    );

  private void setupGlobalKeybindings(GlobalKeybindingProvider provider) {
    var keybindings = config.keybindings().global();

    baseGlobalKeybindings.forEach((keyStrokeStrProducer, runnableSupplier) -> {
      var keyStrokeStr = keyStrokeStrProducer.apply(keybindings);
      if (keyStrokeStr == null) {
        return;
      }
      tryRegisterGlobalKeybinding(provider, keyStrokeStr, runnableSupplier.get());
    });

    if (keybindings.ocr().region() != null) {
      var regions = config.ocr().regions();
      if (regions != null) {
        keybindings.ocr().region().forEach(regionBinding ->
          tryRegisterRegionBindingIfRegionPresent(provider, regionBinding, regions)
        );
      }
    }
  }

  private void tryRegisterRegionBindingIfRegionPresent(
    GlobalKeybindingProvider provider,
    Config.Keybindings.Global.GlobalKeybindingsOCR.RegionBinding regionBinding,
    List<Config.OCR.Region> regions
  ) {
    var maybeRegion = regions.stream()
      .filter(r -> r.symbol().equals(regionBinding.symbol()))
      .findFirst();
    if (maybeRegion.isEmpty()) {
      return;
    }

    var region = maybeRegion.get();
    tryRegisterGlobalKeybinding(
      provider,
      regionBinding.key(),
      () -> recognitionConductor.recognizeRegion(
        // QUAL: (DRY) Copy-pasted from Command parsing
        Rectangle.ofStartAndDimensions(
          region.x(),
          region.y(),
          region.width(),
          region.height()
        ),
        region.autoNarrow()
      )
    );
  }

  private static void tryRegisterGlobalKeybinding(
    GlobalKeybindingProvider provider, String binding, Runnable handler
  ) {
    try {
      provider.registerKeybinding(binding, handler);
    } catch (InvalidKeyStrokeException e) {
      LOG.error(
        "Could not register global keybinding: {} - does not represent a valid KeyStroke",
        binding
      );
      return;
    }
    LOG.info("Registered global keybinding: {}", binding);
  }

  private record PreconfigArgs(
    boolean debug,
    List<String> profileNames,
    boolean regionHelper,
    boolean countChars
  ) {}

  private static PreconfigArgs processPreconfigArgs(Map<String, String> args) {
    var rawDebug = args.get(PRECONFIG_ARGS.get("debug"));
    var debug = isArgValueTruthy(rawDebug);
    if (debug) {
      var loggingExtent =
        "all".equalsIgnoreCase(rawDebug)
        ? DebugLoggingExtent.EVERYTHING
        : DebugLoggingExtent.APP;
      enableDebugLogging(loggingExtent);
    }

    List<String> profileNames = new ArrayList<>(4);
    var profileRaw = args.get(PRECONFIG_ARGS.get("profile"));
    if (profileRaw != null && !profileRaw.isEmpty()) {
      profileNames = Arrays.asList(profileRaw.split(","));
    }

    var regionHelper = isArgValueTruthy(args.get(PRECONFIG_ARGS.get("regionHelper")));

    var countChars = isArgValueTruthy(args.get(PRECONFIG_ARGS.get("countChars")));

    return new PreconfigArgs(debug, profileNames, regionHelper, countChars);
  }

  // QUAL: Move?
  private static final List<String> FALSY_STRINGS = List.of("false", "no", "off");

  private static boolean isArgValueTruthy(String value) {
    return value != null && !FALSY_STRINGS.contains(value);
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
