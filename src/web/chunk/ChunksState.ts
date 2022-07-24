import { batch, createEffect, createMemo, createSignal, on } from "solid-js";
import { createStore, produce } from "solid-js/store";

import type { Backend, ChunkWithFuriganaMessage, MaybeRuby } from "~/backend";
import { notify } from "~/common/notify";
import { BG_FLASH_DURATION_MS } from "~/globalStyles";
import { getSetting, type Setting } from "~/settings";

import { chunk, type Chunk } from "./Chunk";
import { chunkRawTextToClipboardForm } from "./clipboard";
import { incomingChunkIsAReplay } from "./mediaPlayer";
import { ChunkText, type RawChunkText } from "./Text";
import { createChunkTextSelectionState } from "./TextSelectionState";
import { ChunkTranslation, type ChunkTranslationSegment } from "./Translation";

// Keep this many chunks
const MAX_SIZE = 50;

// Keep the current translation as the new chunk's translation as long as the translation isn't
// older than this duration
const TRANSLATION_KEEP_AROUND_TIME_S = 7;

// Keep the previous translation as additional context for the current translation as long as
// the previous translation isn't older than this duration
const TRANSLATION_CONTEXT_KEEP_AROUND_TIME_S = 11;

// Allow initial chunk translation segment to precede the chunk itself by this duration
const TRANSLATION_INITIAL_MAX_ADVANCE_S = 0.9;

// Allow initial chunk translation segment to be follow the chunk itself by this duration
const TRANSLATION_INITIAL_MAX_DELAY_S = 0.9;

export type ChunksState = ReturnType<typeof createChunksState>;

interface CreateChunksStateParams {
  backend: Backend,
  settings: Setting[],
  inputSelection: () => [number, number] | undefined,
  allowedToFlash: () => boolean,
}

export function createChunksState(
  { backend, settings, inputSelection, allowedToFlash }: CreateChunksStateParams
) {
  // === STATE ====================================================================================

  // Main storage of pieces of text
  const [chunks, setChunks] =
    createStore<Chunk[]>([
      chunk({ text: ChunkText.of("") })
    ]);

  // Index of the chunk treated as `current` and displayed in the main area.
  // `equals: false` is needed so that chunk history selection is updated properly when the main
  // storage is updated while being at max size
  const [pointer, setPointer] =
    createSignal(0, { equals: false });

  // Whether the current chunk is being edited
  const [editing, setEditing] =
    createSignal(false);

  // Text from the edit mode to be committed to the current chunk once editing is finished
  const [editText, setEditText] =
    createSignal("");

  // Whether we're waiting for an incoming chunk
  const [waiting, setWaiting] =
    createSignal(false);

  // Reflects the of browser-native text selection within the current chunk
  const [textHighlight, setTextHighlight] =
    createSignal<[number, number]>();

  // State of the flash animation for the current chunk
  const [currentFlashState, setCurrentFlashState] =
    createSignal<ChunkFlashState>({ kind: "not-flashing" });

  // Whether the current chunk's translation is being selected in
  const [selectingInTranslation, setSelectingInTranslation] =
    createSignal(false);

  // The chunk displayed in the main area
  const current = (): Chunk => chunks[pointer()];

  // Selection within the current chunk
  const textSelection = createChunkTextSelectionState({ current, textHighlight });

  // Either the text selected in the current chunk or its entire text if a selection is absent.
  // The name is to be understood as "the current chunk's effective text"
  const currentEffectiveText = (): string => {
    const currText = current().text.base;
    const range = textSelection.get()?.range;
    if (range) {
      const [start, end] = range;
      return currText.substring(start, end + 1);
    }
    return currText;
  };

  // Either the `currentEffectiveText` or, if chunks other than current are selected in chunk
  // history window, the combined text of the selected chunks. This is the text used for lookups
  const effectiveText = (): string => effectiveTextSegs().join("\n");

  const effectiveTextSegs = (): string[] =>
    selectionInfo().otherThanCurrentSelected
      ? chunks.filter(c => c.selected).map(c => c.text.base)
      : [currentEffectiveText()];

  // Same as `effectiveText`, except prefers original text for the chunks that have it
  const originalEffectiveText = (): string => originalEffectiveTextSegs().join("\n");

  const originalEffectiveTextSegs = (): string[] =>
    chunks.filter(c => c.selected).map(c => c.originalText ?? c.text.base);

  // The current chunk's translation plus selected segments of the previous chunk's translation
  const translationWithContext = createMemo<CurrentTranslationWithContext | undefined>(() => {
    const prev = chunks[pointer() - 1]?.translation;

    const curr = current().translation;
    if (!prev && !curr) {
      return undefined;
    }

    let prevFilteredSegments: ChunkTranslationSegment[] | undefined = undefined;
    if (prev) {
      prevFilteredSegments = prev.segments.filter(seg => {
        if (!seg.playbackTimeS && !getSetting(settings, "translation-only-mode")) {
          return false;
        }

        if (seg.playbackTimeS) {
          // Reject segments that are, in terms of the current chunk's playback time, either in the
          // future or too old
          const currPlaybackTime = current().playbackTimeS;
          if (currPlaybackTime) {
            const delta = currPlaybackTime - seg.playbackTimeS;
            if (delta < 0 || delta > TRANSLATION_CONTEXT_KEEP_AROUND_TIME_S) {
              return false;
            }
          }
        }

        // Reject segments that are present in the current translation
        if (curr && curr.segments.some(cs => ChunkTranslation.segmentsSame(seg, cs))) {
          return false;
        }

        return true;
      });
    }

    return {
      current: curr?.text,
      previous: prevFilteredSegments && ChunkTranslation.segmentsToString(prevFilteredSegments)
    };
  });

  // A translation segment that could be for a chunk that's to arrive shortly
  let pendingTranslation: Required<ChunkTranslationSegment> | undefined = undefined;

  // Indicators relating to user selection within the chunk history view
  const selectionInfo = createMemo<SelectionInfo>(() => {
    const selectedIndices: number[] = [];
    let someHaveOriginalText = false;
    let otherThanCurrentSelected = false;
    const p = pointer();
    chunks.forEach((c, i) => {
      if (c.selected) {
        if (i !== p) {
          otherThanCurrentSelected = true;
        }
        if (c.originalText) {
          someHaveOriginalText = true;
        }
        selectedIndices.push(i);
      }
    });
    return { selectedIndices, someHaveOriginalText, otherThanCurrentSelected };
  });

  // === EFFECTS ===================================================================================

  createEffect(on(pointer, selectOnlyCurrent));

  // Make those two mutually exclusive: 1) text selection within the current chunk, and 2) selection
  // within chunk history window involving more than just the current chunk. This is necessary to
  // unambiguously determine `effectiveText`
  createEffect(() => textSelection.get() && selectOnlyCurrent());
  createEffect(() => selectionInfo().otherThanCurrentSelected && textSelection.set(undefined));

  // === PUBLIC FUNCTIONS ==========================================================================

  type InsertRestParams = {
    op: InsertOp,
    altOp?: BasicInsertOp,
    original?: string,
    playbackTimeS?: number,
  }
  & InsertOptions;

  type BasicInsertOp = "append" | "overwrite";
  type SelectionSpecificInsertOp = "replace-selected";
  type InsertOp = BasicInsertOp | SelectionSpecificInsertOp;

  type InsertOptions = {
    allowUnchangedText?: boolean,
    flash?: boolean,
    mayRequestEnhancement?: boolean,
    ignoreEditing?: boolean,
    inPlace?: boolean,
    keepTranslation?: boolean,
  };

  async function insert(
    input: RawChunkText | ((currText: string) => string),
    params: InsertRestParams
  ) {
    // Default values
    if (!params.altOp) {
      params.altOp = "overwrite";
    }
    if (params.allowUnchangedText === undefined) {
      params.allowUnchangedText = false;
    }
    if (params.flash === undefined) {
      params.flash = false;
    }
    if (params.mayRequestEnhancement === undefined) {
      params.mayRequestEnhancement = true;
    }
    if (params.ignoreEditing === undefined) {
      params.ignoreEditing = false;
    }
    if (params.inPlace === undefined) {
      params.inPlace = false;
    }
    if (params.keepTranslation === undefined) {
      params.keepTranslation = false;
    }

    // Reject a likely repeat from the media player (e.g., when replaying from the start of the
    // subtitle)
    if (
      typeof input === "string"
      && params.playbackTimeS !== undefined
      && incomingChunkIsAReplay(input, params.playbackTimeS, current())
    ) {
      return;
    }

    // Transform the input according to the selected operation
    let transformedInput: RawChunkText = "";
    const currText = current().text.base;
    switch (typeof input) {
      case "string": {
        if (!params.ignoreEditing && editing()) {
          // Simple special case, handle and exit
          setEditText(curr => applyInsertOp(params.op, input, curr, inputSelection()));
          return;
        } else {
          if (params.op === "replace-selected" && !textSelection.get()) {
            params.op = params.altOp;
          }
          transformedInput = applyInsertOp(params.op, input, currText, textSelection.get()?.range);
        }
        break;
      }
      case "object":
        transformedInput = input;
        break;
      case "function":
        transformedInput = input(currText);
        break;
    }

    let newOriginalText = params.original;
    if (typeof transformedInput === "string") {
      if (transformedInput !== input) {
        // The input text has been modified, the provided original ver. no longer applies
        newOriginalText = undefined;
      }
      if (!newOriginalText && transformedInput.includes("\n")) {
        // The default copy action copies the text without newlines. So if it has newlines, allow
        // for copying it with newlines through the "copy original" action.
        newOriginalText = transformedInput;
      }

      if (!params.allowUnchangedText && transformedInput === current().text.base) {
        return;
      }
    }

    let shouldEnhance =
      params.mayRequestEnhancement
      && getSetting(settings, "show-furigana") === true
      && typeof transformedInput === "string" // Otherwise this is was already enhanced text
      && transformedInput !== "";
    let newRawText;
    if (shouldEnhance && !waiting()) {
      newRawText = await fetchEnhanced(transformedInput as string);
      shouldEnhance = false; // No need to enhance at the end of this function
    } else {
      // If `waiting`, we will enhance as a separate step at the end of this function to avoid
      // flashing old content
      newRawText = transformedInput;
    }
    const newText = ChunkText.of(newRawText);

    // Determine the new chunk's initial translation
    let newTranslation: ChunkTranslation | undefined = undefined;
    const currTranslation = current().translation;
    if (params.keepTranslation) {
      newTranslation = currTranslation;
    } else {
      // Check for pending advanced translation from playback
      if (params.playbackTimeS) {
        if (pendingTranslation) {
          const sinceArrival = params.playbackTimeS - pendingTranslation.playbackTimeS;
          if (sinceArrival < TRANSLATION_INITIAL_MAX_ADVANCE_S) {
            newTranslation = ChunkTranslation.withSegment(pendingTranslation);
          }
          pendingTranslation = undefined;
        }
      }

      // It's possible that the current translation spans both the current chunk and this new chunk.
      // If that's likely, keep the current translation for now.
      if (params.playbackTimeS && currTranslation) {
        const currTranslationTime = currTranslation.lastSegmentPlaybackTimeS;
        if (currTranslationTime) {
          const delta = params.playbackTimeS - currTranslationTime;
          if (delta > 0 && delta <= TRANSLATION_KEEP_AROUND_TIME_S) {
            newTranslation = currTranslation.decaying();
          }
        }
      }
    }

    const initialInsert = chunks.length === 1 && current().text.base === "";
    const inPlace = params.inPlace || initialInsert;
    batch(() => {
      if (
        params.flash
        && transformedInput !== ""
        && transformedInput !== current().text.base
      ) {
        flashEffectiveCurrent();
      }

      const newChunk = chunk({
        text: newText,
        translation: newTranslation,
        originalText: newOriginalText,
        playbackTimeS: params.playbackTimeS,
      });

      if (inPlace) {
        newChunk.selected = current().selected;
        setChunks(pointer(), newChunk);
      } else {
        // Append the new chunk while keeping the collection's size limited to MAX_SIZE
        setChunks(produce(curr => {
          if (curr.push(newChunk) > MAX_SIZE) {
            curr.shift();
          }
          return curr;
        }));
      }

      textSelection.set(undefined);
    });
    if (!inPlace) {
      setPointer(chunks.length - 1);
    }

    // Remove those translation segments from the n-2 chunk that were supplemented to its
    // translation but later turned out to be intended as for the n-1 chunk. We can only do this
    // with hindsight once chunk n arrives, because earlier, at the time chunk n-1 arrives, it's
    // still possible that its translation will arrive delayed
    if (chunks.length > 3) {
      const subsequentTr = chunks[chunks.length - 2].translation;
      if (subsequentTr) {
        setChunks(
          chunks.length - 3,
          "translation",
          tr => tr?.removingTentativeSegmentsSharedWith(subsequentTr)
        );
      }
    }

    if (shouldEnhance) {
      void enhanceCurrent();
    }
  }

  async function enhanceCurrent() {
    if (current().text.hasFurigana) {
      return;
    }
    void insert(
      await fetchEnhanced(current().text.base),
      {
        op: "overwrite",
        inPlace: true,
        keepTranslation: getSetting(settings, "translation-only-mode")
      }
    );
  }

  function unenhanceCurrent() {
    if (!current().text.hasFurigana) {
      return;
    }
    void insert(current().text.base, { op: "overwrite", inPlace: true, allowUnchangedText: true });
  }

  function handleIncomingTranslation(text: string, playbackTimeS: number | null) {
    if (getSetting(settings, "translation-only-mode")) {
      void insert("", {
        op: "overwrite",
        playbackTimeS: playbackTimeS ?? undefined,
        allowUnchangedText: true
      });
      initTranslationOfLatest({ text, playbackTimeS: playbackTimeS ?? undefined });
      return;
    }

    if (!playbackTimeS) {
      initTranslationOfLatest({ text });
      return;
    }

    const segment = { text, playbackTimeS: playbackTimeS ?? undefined };

    const lastChunkPlaybackTime = chunks[chunks.length - 1].playbackTimeS;
    if (!lastChunkPlaybackTime) {
      initTranslationOfLatest(segment);
      return;
    }

    const timeSinceLastChunk = playbackTimeS - lastChunkPlaybackTime;
    if (timeSinceLastChunk < 0 || timeSinceLastChunk <= TRANSLATION_INITIAL_MAX_DELAY_S) {
      // Left condition true: We're either rewinding or playing a new video. Simply replacing the
      // translation with what has arrived is probably the safest option.
      // Right condition true: Assume this is the initial segement for the most recent chunk's
      // translation, since it arrived just following it
      initTranslationOfLatest(segment);
    } else {
      // Assume this segment is either the intial one for the upcoming chunk's translation or a
      // supplementary one for the most recent chunk's translation, since it arrived significantly
      // after the most recent chunk. Append it to the latest chunk's translation for now and store
      // it for later
      pendingTranslation = segment as Required<ChunkTranslationSegment>;
      const tentativeSegment = { ...segment, tentative: true };
      setChunks(
        chunks.length - 1,
        "translation",
        curr => curr
          ? curr.supplementedWith(tentativeSegment)
          : ChunkTranslation.withSegment(tentativeSegment)
      );
    }
  }

  function startEditing() {
    if (editing()) {
      return;
    }
    batch(() => {
      textSelection.set(undefined);
      setEditText(current().text.base);
      setEditing(true);
    });
  }

  function finishEditing() {
    if (!editing()) {
      return;
    }
    const translationOnlyMode = getSetting<boolean>(settings, "translation-only-mode");
    void insert(editText(), {
      op: "overwrite",
      ignoreEditing: true,
      mayRequestEnhancement: false,
      inPlace: translationOnlyMode,
      keepTranslation: translationOnlyMode
    });
    if (getSetting(settings, "show-furigana") === true) {
      // Enhance separately to avoid flashing old content when waiting for enhance response
      void enhanceCurrent();
    }
    setEditing(false);
  }


  function deleteSelectedText() {
    const selRange = textSelection.get()?.range;
    if (!selRange) {
      return;
    }
    const [start, end] = selRange;
    void insert((curr) => curr.slice(0, start) + curr.slice(end + 1), { op: "overwrite" });
  }

  function deleteEverySecondCharacter() {
    let [start, end] = [0, current().text.length + 1];
    const selRange = textSelection.get()?.range;
    if (selRange) {
      [start, end] = selRange;
    }

    void insert((curr) => {
      const deduplicated = curr
        .slice(start, end)
        .split("")
        .map((c, i) => i % 2 === 0 ? c : "")
        .join("");
      return curr.slice(0, start) + deduplicated + curr.slice(end + 1);
    }, { op: "overwrite" });
  }

  function duplicateSelectedText() {
    const selRange = textSelection.get()?.range;
    if (!selRange) {
      return;
    }
    const [start, end] = selRange;

    void insert((curr) => {
      const toDuplicate = curr.slice(start, end + 1);
      return curr.slice(0, start) + toDuplicate + toDuplicate + curr.slice(end + 1);
    }, { op: "overwrite" });
  }

  function copyTextToClipboard() {
    copyToClipboard("text");
  }

  function copyOriginalTextToClipboard() {
    copyToClipboard("original-text");
  }

  function select(idx: number, value: boolean) {
    setChunks(idx, "selected", value);
  }

  function selectOnlyCurrent() {
    batch(() => {
      setChunks({ from: 0, to: chunks.length - 1 }, "selected", false);
      setChunks(pointer(), "selected", true);
    });
  }

  function canTravelBy(step: number): boolean {
    return step === 0 || !newPointer(step).wasClamped;
  }

  function travelBy(step: number) {
    if (step === 0) {
      return;
    }
    setPointer(newPointer(step).value);
  }

  function travelTo(idx: number) {
    if (idx < 0 || idx >= chunks.length) {
      return;
    }
    setPointer(idx);
  }

  // === PRIVATE FUNCTIONS =========================================================================

  function applyInsertOp(
    op: InsertOp, input: string, curr: string, selection?: [number, number]
  ): string {
    switch (op) {
      case "replace-selected": {
        const [start, end] = selection!;
        return curr.slice(0, start) + input + curr.slice(end + 1);
      }
      case "append":
        return curr + input;
      case "overwrite":
        return input;
    }
  }

  async function fetchEnhanced(t: string): Promise<MaybeRuby[]> {
    if (t === "") {
      return [];
    }
    const res = await backend.request<ChunkWithFuriganaMessage>(
      { kind: "add-furigana", body: { text: t } }
    );
    return res.chunkWithFurigana.maybeRubies;
  }

  type NewPointerResult = {
    value: number,
    wasClamped: boolean,
  };
  function newPointer(step: number): NewPointerResult {
    let value = pointer();
    let wasClamped = true;
    value += step;
    if (value >= chunks.length) {
      value = chunks.length - 1;
    } else if (value < 0) {
      value = 0;
    } else {
      wasClamped = false;
    }

    return { value, wasClamped };
  }

  function initTranslationOfLatest(translationSegment: ChunkTranslationSegment) {
    setChunks(chunks.length - 1, "translation", ChunkTranslation.withSegment(translationSegment));
  }

  type CopyToClipboardMode = "text" | "original-text";
  function copyToClipboard(mode: CopyToClipboardMode) {
    const textSegs =
      mode === "text"
        ? effectiveTextSegs()
        : originalEffectiveTextSegs();
    const text = textSegs
      .map(s => chunkRawTextToClipboardForm(s))
      .join("\n");
    if (text !== "") {
      void navigator.clipboard.writeText(text);
      const originalPart = mode === "original-text" ? "original " : "";
      notify("info", `Copied ${originalPart}text to clipboard`);

      const selInfo = selectionInfo();
      selInfo.otherThanCurrentSelected
        ? flashInHistory(selInfo.selectedIndices)
        : flashEffectiveCurrent();
    }
  }

  function flashEffectiveCurrent() {
    if (!allowedToFlash()) {
      return;
    }
    const sel = textSelection.get();
    const flashState: ChunkFlashState = sel && !textSelection.isWholeTextSelected()
      ? { kind: "range-flashing", range: sel?.range }
      : { kind: "whole-flashing" };
    setCurrentFlashState(flashState);
    setTimeout(() => setCurrentFlashState({ kind: "not-flashing" }), BG_FLASH_DURATION_MS);
  }

  function flashInHistory(indices: number[]) {
    if (!allowedToFlash()) {
      return;
    }
    setChunks(indices, "historyFlashing", true);
    setTimeout(() => setChunks(indices, "historyFlashing", false), BG_FLASH_DURATION_MS);
  }

  // ===============================================================================================

  return {
    chunks,
    //setChunks,
    pointer,
    //setPointer,
    editing,
    //setEditing,
    editText,
    setEditText,
    waiting,
    setWaiting,
    textHighlight,
    setTextHighlight,
    currentFlashState,
    //setCurrentFlashState,
    selectingInTranslation,
    setSelectingInTranslation,

    current,
    currentEffectiveText,
    effectiveText,
    originalEffectiveText,
    translationWithContext,
    selectionInfo,

    insert,
    enhanceCurrent,
    unenhanceCurrent,
    handleIncomingTranslation,
    startEditing,
    finishEditing,
    deleteSelectedText,
    deleteEverySecondCharacter,
    duplicateSelectedText,
    copyTextToClipboard,
    copyOriginalTextToClipboard,
    select,
    selectOnlyCurrent,
    canTravelBy,
    travelBy,
    travelTo,

    textSelection,
  };
}

export type CurrentTranslationWithContext = { current?: string, previous?: string };

type SelectionInfo = {
  selectedIndices: number[],
  someHaveOriginalText: boolean,
  otherThanCurrentSelected: boolean,
};

export type ChunkFlashState =
  | { kind: "not-flashing" }
  | { kind: "whole-flashing" }
  | ChunkRangeFlashingState;

export type ChunkRangeFlashingState = { kind: "range-flashing", range: [number, number] };

