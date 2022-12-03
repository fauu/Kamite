import {
  batch, createEffect, createMemo, createSignal, on, onMount, Show, untrack, type VoidComponent
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
  CharacterCounter as BackendCharacterCounter, ChunkVariant, Command, Config, InMessage,
  PlayerStatus, RecognizerStatus, UILayout
} from "~/backend";
import {
  Backend, BackendNotConnectedScreen, parseBackendConstant, parseRecognizerStatus,
  playerStatusGotConnected
} from "~/backend";
import {
  ChunkCurrentTranslationSelectionParentClass, ChunkView, createChunksState, type Chunk
} from "~/chunk";
import {
  availableCommandPaletteCommands, CommandPalette, commandPrepareForDispatch
} from "~/command";
import { YomichanSentenceDelimiter } from "~/common";
import { TooltipView } from "~/common/floating";
import { notifyUser, type NotificationToastKind } from "~/common/notify";
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
import { ChunkCharIdxAttrName, ChunkLabelId, RootId } from "./dom";
import { SHOW_FURIGANA_DISABLED_MSG } from "./features";
import { useGlobalTooltip } from "./GlobalTooltip";
import { createTheme, themeLayoutFlipped } from "./theme";

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
    createSignal<BackendCharacterCounter>();
  const [chunkVariants, setChunkVariants] =
    createSignal<ChunkVariant[]>([]);
  const [focusMode, setFocusMode] =
    createSignal(false);
  const [middleMouseButtonLikelyDown, setMiddleMouseButtonLikelyDown] =
    createSignal(false);
  const [movingMouseWhilePrimaryDown, setMovingMouseWhilePrimaryDown] =
    createSignal(false);
  const [debugMode, setDebugMode] =
    createSignal(false);

  let rootEl: HTMLDivElement;
  let mainSectionEl: HTMLDivElement | undefined;
  let commandPaletteEl: HTMLDivElement | undefined;
  let actionPaletteEl: HTMLDivElement | undefined;
  let chunkInputEl: HTMLTextAreaElement | undefined;
  let chunkLabelAndTranslationEl: HTMLDivElement;
  let statusPanelEl!: HTMLDivElement;
  let chunkPickerEl!: HTMLDivElement;

  let mouseY = 0;

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
  const sessionTimer = createSessionTimerState();
  const notebook     = createNotebookState({ chunks, settings });
  const debug        = createDebugState();

  const statusPanelFader = createStatusPanelFader({
    chunkLabelAndTranslationEl: () => chunkLabelAndTranslationEl,
    statusPanelEl: () => statusPanelEl,
    notebook,
    chunks,
  });

  const availableCommands =
    createMemo(() => availableCommandPaletteCommands(
      chunks,
      recognizerStatus(),
      playerStatus(),
      config(),
    ));
  const availableActions =
    createMemo(() => getAvailableActions(chunks));
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
  }));

  createEffect(() => chunks.setWaiting(recognizerStatus().kind === "processing"));

  createEffect(() => notebook.setTabHidden("debug", !debugMode()));

  // === ON MOUNT =================================================================================

  onMount(() => {
    if (import.meta.env.DEV) {
      setTimeout(() =>
        void chunks.insert(
          "電車に乗ることや店で食事をすることなどができます。氷",
          { op: "overwrite", flash: true },
        ),
        300
      );
    }

    // Catch in capturing phase so that when we exit chunk edit mode by clicking on a lookup button
    // the editing changes are committed before the lookup button handler initiates lookup
    rootEl.addEventListener("click", handleRootClick, { capture: true });

    integrateClipboardInserter(
      /* onText */ text => backend.command({ kind: "chunk_show", params: { chunk: text } })
    );
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
    switch (event.button) {
      case 0: { // Left
        const target = event.target as HTMLElement;

        const charIdxS = target.dataset[ChunkCharIdxAttrName];
        if (charIdxS) { // Mouse over chunk character
          // Initiate selection starting inside chunk label
          const charIdx = parseInt(charIdxS);
          chunks.textSelection.set({ range: [charIdx, charIdx], anchor: charIdx });
        } else {
          if (chunks.editing() && commandPaletteEl && commandPaletteEl.contains(target)) {
            // Prevent deselecting chunk input text so that the selected part can be replaced with
            // the result of an OCR command issued by command palette click
            event.preventDefault();
            return;
          }

          // Clear chunk selection
          if (chunks.textSelection.get()) {
            const insideSelectionClearningEl =
              mainSectionEl!.contains(target)
              && !commandPaletteEl?.contains(target)
              && !actionPaletteEl?.contains(target);
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
      case 1: { // Middle
        setMiddleMouseButtonLikelyDown(true);
      }
    }
  };

  const handleRootMouseUp = (event: MouseEvent) => {
    switch (event.button) {
      case 0: // Left
        chunks.textSelection.finish();
        notebook.setResizing(false);
        break;
      case 1: // Middle
        window.setTimeout(() => setMiddleMouseButtonLikelyDown(false));
        break;
    }
  };

  const handleRootMouseMove = (event: MouseEvent) => {
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
        let charIdxStr: string | undefined;
        if (el.dataset) {
          charIdxStr = el.dataset[ChunkCharIdxAttrName];
        }
        if (charIdxStr) { // Mouse over chunk character
          // Update chunk selection
          const anchor =
            chunks.textSelection.inProgress()
            ? chunks.textSelection.get()!.anchor!
            : event.movementY > 0 // Moving from above chunk or from below?
              ? 0
              : chunks.current().text.length - 1;
          const range = [parseInt(charIdxStr), anchor] as [number, number];
          range.sort((a, b) => a - b);
          chunks.textSelection.set({ range, anchor });
        } else if (chunks.textSelection.inProgress()) {
          // Update chunk selection
          // QUAL: Is there a better way of passing this?
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
        const defaultVariantContent = defaultVariant.content.replaceAll("@", "");

        if (getSetting(settings, "translation-only-mode")) {
          chunks.handleIncomingTranslation(defaultVariantContent, msg.playbackTimeS);
          break;
        }

        void chunks.insert(
          defaultVariantContent,
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
        chunks.handleIncomingTranslation(msg.translation, msg.playbackTimeS);
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
      case "select-highlighted":
        chunks.textSelection.selectHighlighted();
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
    backend.command("session-timer_toggle-pause");
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
      case "layout":
        setTheme("layout", value as UILayout);
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
        chunks.setRubyConcealed(value as boolean);
        break;
    }

    updateChildSettingsDisabled(setting, setSettings);
  };

  function handleChunkAdded(chunk: Chunk) {
    // PERF: Only needed when chunk logging enabled (as of 2022-09-17)
    backend.notify({ kind: "chunk-added", body: { chunk: chunk.text.base } });
  }

  window.addEventListener("resize", () => {
    mainSectionEl && notebook.syncHeight();
    statusPanelFader.setFadeInvalidated();
  });

  // DRY: Unify with action handling
  document.addEventListener("keydown", event => {
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
          let allowDefault = true;
          if (event.altKey) {
            allowDefault = false;
            const a = "copy-original";
            if (availableChunkHistoryActions().includes(a)) {
              handleChunkHistoryAction(a);
            } else if (actionsInclude(availableActions(), a)) {
              chunks.copyOriginalTextToClipboard();
            } else {
              allowDefault = true;
            }
          }
          if (allowDefault) {
            if (chunks.selectingInTranslation()) {
              // Allow default
            } else if (actionsInclude(availableActions(), "copy-selected")) {
              chunks.copyTextToClipboard();
            } else if (availableChunkHistoryActions().includes("copy")) {
              handleChunkHistoryAction("copy");
            } else if (actionsInclude(availableActions(), "copy-all")) {
              chunks.copyTextToClipboard();
            }
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

    // Register the extent of a browser native selection in chunk (from Yomichan hover, etc.)
    const selectingInChunk = anchorParentEl?.dataset[ChunkCharIdxAttrName] !== undefined;
    if (selectingInChunk) {
      const focusParentEl = selection?.focusNode?.parentElement;
      // ASSUMPTION: Selection always made left-to-right
      chunks.setTextHighlight(
        [anchorParentEl, focusParentEl]
          .map(el => parseInt(el!.dataset[ChunkCharIdxAttrName]!)) as [number, number]
      );
    } else {
      setTimeout(() => {
        // This code might run as a result of clicking the `select-highlighted` action button.
        // The action, however, needs the highlight to work on, so we delay its removal slightly.
        chunks.setTextHighlight(undefined);
      }, 200);
    }

    // Register the fact of a browser native selection in chunk
    const selectingInChunkTranslation =
      anchorParentEl?.classList.contains(ChunkCurrentTranslationSelectionParentClass)
      && selection!.type === "Range";
    chunks.setSelectingInTranslation(selectingInChunkTranslation ?? false);
  });

  window.addEventListener("blur", () => {
    globalTooltip.hide();
    if (notebook.isCollapseAllowed()) {
      notebook.setCollapsed(true);
    }
  });

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
      ref={el => rootEl = el}
    >
      <GlobalStyles />
      <Show when={backend.connectionState() !== "connected"}>
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
          onInput={handleChunkInput}
          inputRef={el => chunkInputEl = el}
          labelAndTranslationRef={el => chunkLabelAndTranslationEl = el}
          movingMouseWhilePrimaryDown={movingMouseWhilePrimaryDown}
        />
        <StatusPanel
          fade={statusPanelFader.shouldFade()}
          focusMode={focusMode}
          ref={el => statusPanelEl = el}
        >
          <Show when={characterCounter()} keyed>{ counter =>
            <CharacterCounter
              state={counter}
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
        focusMode={focusMode}
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
