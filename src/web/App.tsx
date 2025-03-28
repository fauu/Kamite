import { createScheduled, debounce } from "@solid-primitives/scheduled";
import {
  batch, createEffect, createMemo, createSignal, on, onMount, Show, untrack,
  type Accessor,
  type VoidComponent
} from "solid-js";
import { createStore } from "solid-js/store";
import { css, styled, ThemeProvider } from "solid-styled-components";

import { concealUnlessHovered } from "~/directives";
const [_] = [concealUnlessHovered];

import {
  ActionPalette, actionsInclude, availableActions as getAvailableActions, hiraganaToKatakana,
  katakanaToHiragana, type Action, type ActionInvocation
} from "~/action";
import type {
  CharacterCounter as BackendCharacterCounter, ChunkVariant, Command, Config, EventName, InMessage,
  PlayerStatus, RecognizerStatus
} from "~/backend";
import {
  Backend, BackendNotConnectedScreen, defaultCharacterCounter, parseBackendConstant,
  parseRecognizerStatus, playerStatusGotConnected
} from "~/backend";
import {
  ChunkCurrentTranslationSelectionParentClass,
  ChunkView, createChunksState, type Chunk,
  type ChunkTextSelection
} from "~/chunk";
import { ChunkLabel } from "~/chunk/label";
import {
  availableCommandPaletteCommands, CommandPalette, commandPrepareForDispatch
} from "~/command";
import { YomichanSentenceDelimiter } from "~/common";
import { TooltipView } from "~/common/floating";
import { notifyUser, type NotificationToastKind } from "~/common/notify";
import { MSECS_IN_SECS } from "~/common/time";
import {
  ChunkHistory, ChunkPicker, createDebugState, createNotebookState, Debug, Notebook
} from "~/notebook";
import {
  availableChunkHistoryActions as getAvailableChunkHistoryActions, type ChunkHistoryAction
} from "~/notebook/chunk-history";
import {
  DEFAULT_SETTINGS, disableSetting, getSetting, Settings, updateChildSettingsDisabled, type Setting,
  type SettingChangeRequest
} from "~/settings";
import {
  CharacterCounter, createSessionTimerState, createStatusPanelFader, SessionTimer, StatusPanel
} from "~/status-panel";
import { ChromeClass, GlobalStyles } from "~/style";

import { integrateClipboardInserter } from "./clipboardInserter";
import { ChunkLabelId, RootId } from "./dom";
import { createEventNotifier } from "./eventNotifier";
import { SHOW_FURIGANA_DISABLED_MSG } from "./features";
import { useGlobalTooltip } from "./GlobalTooltip";
import { createTheme, themeLayoutFlipped } from "./theme";
import { OcrConfigurationSelector } from "./status-panel/OcrConfigurationSelector";

export const App: VoidComponent = () => {

  // === STATE =====================================================================================

  const [config, setConfig] =
    createSignal<Config>();
  const [profileNames, setProfileNames] =
    createSignal<string[]>([]);
  const [recognizerStatus, setRecognizerStatus] =
    createSignal<RecognizerStatus>({ kind: "initializing" });
  const [playerStatus, setPlayerStatus] =
    createSignal<PlayerStatus>("disconnected");
  const [characterCounter, setCharacterCounter] =
    createSignal<BackendCharacterCounter>(defaultCharacterCounter());
  const [chunkVariants, setChunkVariants] =
    createSignal<ChunkVariant[]>([]);
  const [focusMode, setFocusMode] =
    createSignal(false);
  const [middleMouseButtonLikelyDown, setMiddleMouseButtonLikelyDown] =
    createSignal(false);
  const [movingMouseWhilePrimaryDown, setMovingMouseWhilePrimaryDown] =
    createSignal(false);
  const [ impedeClickMoveChunkTextSelect, setImpedeClickMoveChunkTextSelect ] = createSignal(false);
  const [debugMode, setDebugMode] =
    createSignal(false);


  let mainSectionEl: HTMLDivElement | undefined;
  let commandPaletteEl: HTMLDivElement | undefined;
  let actionPaletteEl: HTMLDivElement | undefined;
  let chunkInputEl: HTMLTextAreaElement | undefined;
  let chunkLabelAndTranslationEl: HTMLDivElement;
  let statusPanelEl!: HTMLDivElement;
  let chunkPickerEl!: HTMLDivElement;

  let mouseY = 0;

  let inactivityTimeout: NodeJS.Timeout | undefined;

  const chunkInputSelection = (): [number, number] | undefined =>
    (chunkInputEl && [chunkInputEl.selectionStart, chunkInputEl.selectionEnd - 1]) ?? undefined;

  const [theme, setTheme] = createTheme();

  const themeLayoutFlippedMemo =
    createMemo(() => themeLayoutFlipped(theme));

  const globalTooltip = useGlobalTooltip()!;

  const [settings, setSettings] = createStore<Setting[]>(DEFAULT_SETTINGS);

  const backend = new Backend({ onMessage: handleBackendMessage });

  const chunks = createChunksState({
    backend,
    settings,
    inputSelection: chunkInputSelection,
    allowedToFlash: () => config()?.chunk.flash || false,
    onChunkAdded: handleChunkAdded,
  });
  const sessionTimer  = createSessionTimerState();
  const notebook      = createNotebookState({ chunks, settings });
  const debug         = createDebugState();
  const eventNotifier = createEventNotifier({ backend });

  const statusPanelFader = createStatusPanelFader({
    chunkLabelAndTranslationEl: () => chunkLabelAndTranslationEl,
    statusPanelEl: () => statusPanelEl,
    notebook,
    chunks,
  });
  const statusPanelUrgent = () => characterCounter().frozen || !sessionTimer.running();
  const concealStatusPanelUnlessHovered = () => focusMode() && !statusPanelUrgent();

  const showBackendNotConnectedScreen = () => backend.connectionState() !== "connected";

  const mainUIVisible = () => !showBackendNotConnectedScreen();

  const availableCommands =
    createMemo(() => availableCommandPaletteCommands(
      chunks,
      recognizerStatus(),
      playerStatus(),
      config(),
    ));

  const availableActionsScheduled = createScheduled(fn => debounce(fn, 50));
  const availableActions: Accessor<Action[] | undefined> = createMemo(actions => {
    const newActions = getAvailableActions(chunks);
    return availableActionsScheduled() ? newActions : actions;
  });

  const availableChunkHistoryActions =
    createMemo(() => getAvailableChunkHistoryActions(chunks));

  let revealedChunkPickerTab = false;

  // === EFFECTS ==================================================================================

  createEffect(on(config, c => {
    if (!c) {
      return;
    }
    settings.forEach(s => handleSettingChangeRequest(s.id, s.configKey(c)));

    mainSectionEl && notebook.syncHeight(c);

    if (import.meta.env.DEV && c.dev.serveStaticInDevMode) {
      notifyUser("warning", "serveStaticInDevMode override is enabled");
    }

    notebook.updateLookupTabs(c.lookup?.targets ?? []);

    sessionTimer.setAutoPauseIntervalS(
      (c.sessionTimer.autoPause.enable && c.sessionTimer.autoPause.after) || undefined
    );
  }));

  createEffect(() => chunks.setWaiting(recognizerStatus().kind === "processing"));

  createEffect(() => notebook.setTabHidden("debug", !debugMode()));

  // === ON MOUNT =================================================================================

  onMount(() => {
    import.meta.env.DEV && debugSetup();

    // Catch in capturing phase so that when we exit chunk edit mode by clicking on a lookup button
    // the editing changes are committed before the lookup button handler initiates lookup
    window.root.addEventListener("click", handleRootClick, { capture: true });

    window.mainUIVisible = mainUIVisible;

    integrateClipboardInserter(
      /* onText */ text => backend.command({ kind: "chunk_show", params: { chunk: text } })
    );

    handleActivity();
  });

  function handleMainSectionRef(el: HTMLDivElement) {
    mainSectionEl = el;
    notebook.setMainSectionEl(el);
  }

  // === ON INIT ===================================================================================

  // Apply custom CSS
  const linkEl = document.createElement("link");
  linkEl.rel = "stylesheet";
  linkEl.href = Backend.customCSSUrl();
  document.head.appendChild(linkEl);

  const assumeChrome = !navigator.userAgent.includes("Firefox");

  // === EVENT HANDLERS ============================================================================

  const handleRootClick = ({ target }: MouseEvent) => {
    if (!target) {
      return;
    }
    if (chunks.editing()) {
      // Exit Chunk edit mode with a mouse click except when clicked inside the following elements
      const exceptionalEls = [commandPaletteEl, chunkInputEl, chunkPickerEl];
      const insideExceptionalEl = exceptionalEls.some(el => el && el.contains(target as Node));
      if (!insideExceptionalEl) {
        chunks.finishEditing();
      }
    }
  };

  const handleRootMouseDown = (event: MouseEvent) => {
    handleActivity();

    const targetEl = event.target as HTMLElement;
    switch (event.button) {
      case 0: { // Left
        if (ChunkLabel.isCharElement(targetEl)) {
          const chIdx = ChunkLabel.charIdxOfElement(targetEl)!;

          const selectionRange = chunks.textSelection.get()?.range;
          const clickedCharTheOnlySelected = selectionRange &&
            selectionRange.every(idx => idx === chIdx);
          const newSelection: ChunkTextSelection | undefined = clickedCharTheOnlySelected
            ? undefined
            : { range: [chIdx, chIdx], anchor: chIdx };
          chunks.textSelection.set(newSelection);

          if (!newSelection) {
            // Needed for clicking a single selected chunk char to remove selection and then
            // slightly moving the cursor not to be immediately misinterpreted as initiating
            // selection by pressing M1 before moving the cursor inside the chunk label
            setImpedeClickMoveChunkTextSelect(true);
          }

          // Prevents dragging when selecting caused by syncing with the native browser selection
          event.preventDefault();
        } else {
          if (chunks.editing() && commandPaletteEl && commandPaletteEl.contains(targetEl)) {
            // Prevent deselecting chunk input text so that the selected part can be replaced with
            // the result of an OCR command issued by command palette click
            event.preventDefault();
            return;
          }

          // Clear chunk selection
          if (chunks.textSelection.get()) {
            const insideSelectionClearningEl =
              mainSectionEl!.contains(targetEl)
              && !commandPaletteEl?.contains(targetEl)
              && !actionPaletteEl?.contains(targetEl);
            if (insideSelectionClearningEl) {
              chunks.textSelection.set(undefined);
            }
          }

          if (!notebook.collapsed()) {
            notebook.resizeMaybeStart(themeLayoutFlippedMemo(), mouseY);
          }
        }

        // Clear chunk translation selection
        if (chunks.selectingInTranslation()) {
          document.getSelection()?.removeAllRanges();
        }
      }
        break;

      case 1: // Middle
        setMiddleMouseButtonLikelyDown(true);
        // Prevents characters highlighting when middle-clicking on the chunk
        event.preventDefault();
        break;

      case 2: // Right
        if (assumeChrome) {
          // Prevents characters highlighting when right-clicking on the chunk on Chrome
          event.preventDefault();
        }
        break;
    }
  };

  document.addEventListener("contextmenu", (event: Event) => {
    if (shouldPreventContextMenu(event.target as HTMLElement)) {
      event.preventDefault();
    }
  });

  function shouldPreventContextMenu(targetEl: HTMLElement): boolean {
    if (!chunks.textHighlight()) {
      return true;
    }

    const range = chunks.textSelection.get()?.range;
    if (range) {
      const chIdx = ChunkLabel.charIdxOfElement(targetEl);
      if (chIdx && chIdx >= range[0] && chIdx <= range[1]) {
        return false;
      }
    }
    return true;
  }

  const handleRootMouseUp = (event: MouseEvent) => {
    handleActivity();

    switch (event.button) {
      case 0: // Left
        setImpedeClickMoveChunkTextSelect(false);
        chunks.textSelection.finish();
        notebook.setResizing(false);
        break;
      case 1: // Middle
        window.setTimeout(() => setMiddleMouseButtonLikelyDown(false));
        break;
    }
  };

  const handleRootMouseMove = (event: MouseEvent) => {
    handleActivity();

    mouseY = event.clientY;

    if (notebook.isCollapseAllowed()) {
      notebook.collapseIfNotHovering(themeLayoutFlippedMemo(), mouseY, event.movementY);
    }

    const primaryButton = (event.buttons & 1) === 1;
    setMovingMouseWhilePrimaryDown(primaryButton);
    if (primaryButton) {
      if (notebook.resizing()) {
        notebook.resizeTick(themeLayoutFlippedMemo(), event.movementY);
      } else {
        const el = event.target as HTMLElement;
        if (ChunkLabel.isCharElement(el) && !impedeClickMoveChunkTextSelect()) {
          // Set the chunk selection to include the hovered character
          const anchor =
            chunks.textSelection.inProgress()
            ? chunks.textSelection.get()!.anchor! // Use the existing anchor
            : event.movementY > 0
              ? 0 // New selection from above, use 1st char as anchor
              : chunks.current().text.length - 1; // New sel. from below, use last char as anchor
          const range = [ChunkLabel.charIdxOfElement(el), anchor] as [number, number];
          range.sort((a, b) => a - b);
          chunks.textSelection.set({ range, anchor });
        } else if (chunks.textSelection.inProgress()) {
          // Update chunk selection to include characters between the anchor and the cursor
          // QUAL: Should probably have a reference to ChunkLabel and move some of this code to its
          //       internal methods
          const labelEl = document.getElementById(ChunkLabelId)!;
          const lastChunkElIdx = labelEl.childElementCount - 1;
          const lastChunkEl = labelEl.childNodes[lastChunkElIdx] as HTMLElement;
          const lastRect = lastChunkEl.getBoundingClientRect();
          const cursorRightOfLast = event.clientX > lastRect.right && event.clientY > lastRect.top;
          const anchor = chunks.textSelection.get()!.anchor!;
          if (cursorRightOfLast || /* cursorBelowAll */ event.clientY > lastRect.bottom) {
            chunks.textSelection.set({ range: [anchor, chunks.current().text.length - 1], anchor });
          } else if (event.clientY < labelEl.getBoundingClientRect().top) {
            chunks.textSelection.set({ range: [0, anchor], anchor });
          }
        }
      }
    }
  };

  const handleReconnectClick = () => void backend.connect();

  const handleChunkInput = (newText: string) => chunks.setEditText(newText);

  function handleBackendMessage(msg: InMessage) {
    switch (msg.kind) {
      case "user-notification":
        notifyUser(
          parseBackendConstant(msg.userNotificationKind) as NotificationToastKind,
          msg.content
        );
        break;

      case "chunk-variants": {
        const defaultVariant = msg.variants[0];

        if (getSetting(settings, "translation-only-mode")) {
          chunks.handleIncomingTranslation(defaultVariant.content, "LATEST", msg.playbackTimeS);
          break;
        }

        void chunks.insert(
          defaultVariant.content,
          {
            op: "overwrite-or-replace-selected-in-edit-mode",
            original: defaultVariant.originalContent ?? undefined,
            playbackTimeS: msg.playbackTimeS ?? undefined,
            allowUnchangedText: true,
            flash: true,
          }
        );

        if (msg.variants.length > 1) {
          setChunkVariants(msg.variants.slice(1));
          if (!revealedChunkPickerTab) {
            notebook.setTabHidden("chunk-picker", false);
            revealedChunkPickerTab = true;
          }
          if (notebook.activeTab().id !== "debug") {
            notebook.activateTab("chunk-picker");
          }
        }

        break;
      }

      case "chunk-translation":
        chunks.handleIncomingTranslation(msg.translation, msg.destination, msg.playbackTimeS);
        break;

      case "program-status":
        batch(() => {
          msg.debug !== undefined && setDebugMode(msg.debug);
          msg.sessionTimer && sessionTimer.sync(msg.sessionTimer);
          msg.profileNames && setProfileNames(msg.profileNames);
          msg.characterCounter && setCharacterCounter(msg.characterCounter);
          msg.recognizerStatus && setRecognizerStatus(parseRecognizerStatus(msg.recognizerStatus));
          if (msg.playerStatus) {
            const prevStatus = playerStatus();
            const newStatus = parseBackendConstant(msg.playerStatus as string) as PlayerStatus;
            setPlayerStatus(newStatus);
            if (newStatus !== prevStatus && newStatus === "disconnected") {
              notifyUser("info", "Media player disconnected");
            } else if (playerStatusGotConnected(prevStatus, newStatus)) {
              notifyUser("info", "Connected to media player");
            }
          }
          if (msg.unavailableUniversalFeatures) {
            for (const feature of msg.unavailableUniversalFeatures) {
              if (feature.id === "auto-furigana") {
                disableSetting(settings, "enable-furigana", setSettings, SHOW_FURIGANA_DISABLED_MSG);
              }
            }
          }
          msg.subscribedEvents && eventNotifier.update(msg.subscribedEvents as EventName[]);
        });
        break;

      case "debug-image":
        debug.addImage(msg.imgB64);
        break;

      case "config":
        setConfig(msg.config);
        break;

      case "lookup-request":
        // QUAL: Feels hacky
        notebook.setLookupOverride({ text: msg.customText ?? undefined, symbol: msg.targetSymbol });
        notebook.setLookupOverride(undefined);
        break;
    }
  }

  const handleCommandRequested = (command: Command) =>
    backend.command(commandPrepareForDispatch(command, chunks));

  const handleActionRequested = (action: Action, invocation: ActionInvocation) => {
    switch (action.kind) {
      case "undo":
        chunks.travelBy(-1);
        break;
      case "redo":
        if (invocation === "base") {
          chunks.travelBy(1);
        } else {
          chunks.travelToLast();
        }
        break;
      case "select-all":
        chunks.textSelection.selectAll();
        break;
      case "delete-selected":
        chunks.deleteSelectedText();
        break;
      case "delete-every-second-char":
        chunks.deleteEverySecondCharacter();
        break;
      case "duplicate-selected":
        chunks.duplicateSelectedText();
        break;
      case "copy-all": // Fallthrough
      case "copy-selected":
        chunks.copyTextToClipboard();
        break;
      case "copy-original":
        chunks.copyOriginalTextToClipboard();
        break;
      case "transform-selected":
        void chunks.insert(action.into, { op: "replace-selected" });
        break;
      case "hiragana-to-katakana":
        void chunks.insert(
          hiraganaToKatakana(chunks.currentEffectiveText()),
          { op: "replace-selected" },
        );
        break;
      case "katakana-to-hiragana":
        void chunks.insert(
          katakanaToHiragana(chunks.currentEffectiveText()),
          { op: "replace-selected" },
        );
        break;
    }
  };

  const handleCharacterCounterClick = () => {
    backend.command("character-counter_toggle-freeze");
  };

  const handleCharacterCounterHoldClick = () => {
    backend.command("character-counter_reset");
  };

  const handleSessionTimerClick = () => {
    backend.command("session-timer_toggle");
  };

  const handleSessionTimerHoldClick = () => {
    backend.command("session-timer_reset");
  };

  const handleChunkVariantPick = (text: string) =>
    void chunks.insert(text, { op: "replace-selected" });

  const handleChunkHistoryEntryClick = (idx: number) => {
    chunks.travelTo(idx);
  };

  const handleChunkHistoryAction = (action: ChunkHistoryAction) => {
    switch (action) {
      case "copy":
        chunks.copyTextToClipboard();
        break;
      case "copy-original":
        chunks.copyOriginalTextToClipboard();
        break;
      case "reset-selection":
        chunks.selectOnlyCurrent();
        break;
    }
    chunks.selectOnlyCurrent();
  };

  const handleSettingChangeRequest: SettingChangeRequest = (id, value) => {
    const setting = settings.find(s => s.id === id)!;
    if (setting.disabled?.value) {
      return;
    }

    setSettings(s => s.id === id, "value", value);
    switch (id) {
      case "flip-layout":
        setTheme("layout", value ? "STANDARD_FLIPPED" : "STANDARD");
        break;
      case "focus-mode":
        setFocusMode(value as boolean);
        break;
      case "notebook-collapse":
        if (value) {
          notebook.collapseIfNotHovering(themeLayoutFlippedMemo(), mouseY);
        } else if (value === false) {
          notebook.setCollapsed(false);
        }
        break;
      case "enable-furigana":
        value ? void chunks.enhanceCurrent() : chunks.unenhanceCurrent();
        break;
      case "conceal-furigana":
        chunks.setConcealRubies(value as boolean);
        break;
    }

    updateChildSettingsDisabled(setting, setSettings);
  };

  function handleChunkAdded(chunk: Chunk) {
    // QUAL: Integrate into `eventNotifier`?
    if (eventNotifier.shouldNotify("chunk-add")) {
      backend.eventNotify({ name: "chunk-add", data: { chunkText: chunk.text.base } });
    }
    handleActivity();
  }

  function handleActivity() {
    if (sessionTimer.autoPaused()) {
      handleReturningActivity();
    }
    if (inactivityTimeout) {
      clearTimeout(inactivityTimeout);
    }
    const interval = sessionTimer.autoPauseIntervalS();
    if (interval) {
      inactivityTimeout = setTimeout(handleInactivityIntervalElapsed, interval * MSECS_IN_SECS);
    }
  }

  function handleReturningActivity() {
    sessionTimer.setAutoPaused(false);
    // autoPaused is not reset when unpausing manually, so it might already be running
    if (!sessionTimer.running()) {
      backend.command("session-timer_start");
      notifyUser("info", "Resumed session timer");
    }
  }

  function handleInactivityIntervalElapsed() {
    if (sessionTimer.running()) {
      backend.command("session-timer_stop");
      sessionTimer.setAutoPaused(true);
      notifyUser("info", "Stopped session timer due to inactivity");
    }
  }

  window.addEventListener("resize", () => {
    mainSectionEl && notebook.syncHeight();
    statusPanelFader.setFadeInvalidated();
  });

  // DRY: Unify with action handling
  document.addEventListener("keydown", event => {
    handleActivity();

    let commandToSend: Command["kind"] | undefined = undefined;
    switch (event.code) {
      case "Backspace": // Fallthrough
      case "Delete":
        if (actionsInclude(availableActions(), "delete-selected")) {
          chunks.deleteSelectedText();
        }
        break;

      case "KeyA":
        if (event.ctrlKey && actionsInclude(availableActions(), "select-all")) {
          chunks.textSelection.selectAll();
          event.preventDefault();
        }
        break;

      case "KeyC":
        if (event.ctrlKey) {
          let handled = true;
          let allowDefault = chunks.editing();
          if (event.altKey) {
            handled = false;
            const a = "copy-original";
            if (availableChunkHistoryActions().includes(a)) {
              handleChunkHistoryAction(a);
            } else if (actionsInclude(availableActions(), a)) {
              chunks.copyOriginalTextToClipboard();
            } else {
              handled = true;
            }
          }
          if (handled) {
            if (chunks.selectingInTranslation()) {
              allowDefault = true;
            } else if (actionsInclude(availableActions(), "copy-selected")) {
              chunks.copyTextToClipboard();
            } else if (availableChunkHistoryActions().includes("copy")) {
              handleChunkHistoryAction("copy");
            } else if (actionsInclude(availableActions(), "copy-all")) {
              chunks.copyTextToClipboard();
            }
          }
          if (!allowDefault) {
            event.preventDefault();
          }
        }
        break;

      case "Enter":
        if (event.ctrlKey) {
          if (chunks.editing()) {
            chunks.finishEditing();
          } else {
            chunks.startEditing();
            if (event.shiftKey) {
              chunks.setEditText("");
            }
          }
        }
        break;

      case "Space":
        commandToSend = "player_playpause";
        break;

      case "ArrowLeft":
        commandToSend = event.altKey ? "player_seek-start-sub" : "player_seek-back";
        break;

      case "ArrowRight":
        commandToSend = "player_seek-forward";
        break;
    }
    if (commandToSend && !chunks.editing()) {
      backend.command(commandToSend);
    }
  });

  document.addEventListener("paste", (event: ClipboardEvent) => {
    if (middleMouseButtonLikelyDown()) {
      // Prevent Chrome's middle-click paste.
      // Reset the button state to not break pasting forever in cases when the button is once
      // pressed within Kamite's window but depressed outside it
      setMiddleMouseButtonLikelyDown(false);
      return;
    }
    if (chunks.editing()) {
      return;
    }
    const text = event.clipboardData?.getData("text");
    if (!text || text.trim() === "") {
      return;
    }
    backend.command({ kind: "chunk_show", params: { chunk: text } });
  });

  document.addEventListener("selectionchange", () => {
    const selection = document.getSelection();

    const anchorParentEl = selection?.anchorNode?.parentElement;

    const selectingInChunkPicker = anchorParentEl && chunkPickerEl.contains(anchorParentEl);
    if (selectingInChunkPicker) {
      return;
    }

    const selectingInChunkTranslation =
      anchorParentEl?.classList.contains(ChunkCurrentTranslationSelectionParentClass)
      && selection!.type === "Range";
    chunks.setSelectingInTranslation(selectingInChunkTranslation ?? false);

    const fromKamiteChunkAction = selection && selection?.rangeCount >= 1
      && selection.getRangeAt(0).fromKamiteChunkAction;
    if (!fromKamiteChunkAction) {
      // We want the browser selection to be in sync with main chunk's Kamite text selection. So
      // if the current modification of browser's selection doesn't come from that, we must clear
      // Kamite's selection so that it's not out of sync
      chunks.textSelection.set(undefined);
    }

    // We hide the native browser selection highlight in main chunk due to a Chrome quirk, so we
    // need to emulate it ourselves.
    const nonEmptyBrowserSelectionChangedInMainChunk = anchorParentEl
      && ChunkLabel.isCharElement(anchorParentEl);
    let newHighlight: [number, number] | undefined;
    if (nonEmptyBrowserSelectionChangedInMainChunk) {
      const focusParentEl = selection.focusNode?.parentElement;
      if (focusParentEl) {
        newHighlight = [anchorParentEl, focusParentEl]
          .map(ChunkLabel.charIdxOfElement) as [number, number];
      }
    }
    chunks.setTextHighlight(newHighlight);
  });

  document.documentElement.addEventListener("mouseleave", () => {
    globalTooltip.hide();
    if (notebook.isCollapseAllowed()) {
      notebook.setCollapsed(true);
    }
  });

  // ==============================================================================================

  function debugSetup() {
    setTimeout(() => {
      void chunks.insert(
        "電車に乗ることや店で食事をすることなどができます。氷",
        { op: "overwrite", flash: true },
      )

      setChunkVariants([{
        content: "電車に乗ることや\n店で食事をすることなど",
        originalContent: null,
        labels: ["test"],
        score: 100,
        enhancements: {
          interVariantUniqueCharacterIndices: [3],
          furiganaMaybeRubies: [],
        }
      }]);
      notebook.setTabHidden("chunk-picker", false);
      revealedChunkPickerTab = true;
    }, 200);
  }

  // ==============================================================================================

  return <ThemeProvider theme={theme}>
    <Root
      onMouseDown={handleRootMouseDown}
      onMouseUp={handleRootMouseUp}
      onMouseMove={handleRootMouseMove}
      class={
        [ // Workaround until classList works
          [ChromeClass, assumeChrome],
          ...profileNames().map(n => [`profile-${n}`, true])
        ].map(([name, active]) => active ? name : undefined).filter(x => x).join(" ")
      }
      id={RootId}
      ref={el => window.root = el}
    >
      <GlobalStyles />
      <Show when={showBackendNotConnectedScreen()}>
        <BackendNotConnectedScreen
          connectionState={backend.connectionState()}
          contentDisplayDelayMS={1000}
          dotAnimationStepMS={1000}
          onReconnectClick={handleReconnectClick}
        />
      </Show>
      <MainSection ref={handleMainSectionRef} id="main-section">
        <div id="toolbar" class={ToolbarClass} use:concealUnlessHovered={{ enabled: focusMode }}>
          <Show when={availableCommands().length > 0}>
            <CommandPalette
              commands={availableCommands()}
              mediaPlayerStatus={playerStatus()}
              onCommand={handleCommandRequested}
              ref={el => commandPaletteEl = el}
            />
          </Show>
          <ActionPalette
            actions={availableActions()}
            targetText={untrack(chunks.currentEffectiveText)}
            onAction={handleActionRequested}
            ref={el => actionPaletteEl = el}
          />
          <YomichanSentenceDelimiter/>
        </div>
        <ChunkView
          chunksState={chunks}
          movingMouseWhilePrimaryDown={movingMouseWhilePrimaryDown}
          onInput={handleChunkInput}
          inputRef={el => chunkInputEl = el}
          labelAndTranslationRef={el => chunkLabelAndTranslationEl = el}
        />
        <StatusPanel
          fade={statusPanelFader.shouldFade()}
          concealUnlessHovered={concealStatusPanelUnlessHovered}
          ref={el => statusPanelEl = el}
        >
          <OcrConfigurationSelector recognizerStatus={recognizerStatus} />
          <Show when={characterCounter()} keyed>{counter =>
            <CharacterCounter
              /* QUAL: Pass a unified state with reading pace accessor instead? */
              state={counter}
              timerState={sessionTimer}
              onClick={handleCharacterCounterClick}
              onHoldClick={handleCharacterCounterHoldClick}
              />
          }</Show>
          <SessionTimer
            state={sessionTimer}
            onClick={handleSessionTimerClick}
            onHoldClick={handleSessionTimerHoldClick}
          />
        </StatusPanel>
      </MainSection>
      <Notebook
        state={notebook}
        lookupText={untrack(chunks.effectiveText)}
        chunkPicker={
          <ChunkPicker
            variants={chunkVariants()}
            debug={debugMode()}
            onPick={handleChunkVariantPick}
            ref={chunkPickerEl}
            />
        }
        chunkHistory={
          <ChunkHistory
            state={chunks}
            availableSelectionActions={availableChunkHistoryActions()}
            onEntryClick={handleChunkHistoryEntryClick}
            onAction={handleChunkHistoryAction}
            />
        }
        settings={
          <Settings store={settings} onSettingChangeRequested={handleSettingChangeRequest} />
        }
        debug={<Debug state={debug} />}
        concealUnlessHovered={() => !notebook.resizing() && focusMode()}
        />
      <TooltipView state={globalTooltip} />
    </Root>
  </ThemeProvider>;
};

const Root = styled.div`
  background: var(--color-bg);
  color: var(--color-fg);
  position: relative;
  display: flex;
  min-height: 100vh;
  max-height: 100vh;
  flex-direction: ${p => !themeLayoutFlipped(p.theme) ? "column" : "column-reverse"};
`;

const MainSection = styled.div`
  display: flex;
  flex: 1;
  position: relative;
  flex-direction: column;
  min-height: 0;
  flex-direction: ${p => !themeLayoutFlipped(p.theme) ? "column" : "column-reverse"};
`;

const ToolbarClass = css`
  position: relative; /* For focus mode cover */
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  width: 100vw;
`;
