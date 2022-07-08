import { createSignal, type Accessor } from "solid-js";

import type { Chunk } from "./Chunk";

export type ChunkTextSelectionState = ReturnType<typeof createChunkTextSelectionState>;

export type ChunkTextSelection = {
  range: [number, number],
  anchor?: number,
};

export function chunkTextSelectionEquals(a: ChunkTextSelection, b: ChunkTextSelection): boolean {
  return a.range[0] === b.range[0]
    && a.range[1] === b.range[1]
    && a.anchor === b.anchor;
}

interface CreateChunkTextSelectionStateParams {
  current: () => Chunk,
  textHighlight: Accessor<[number, number] | undefined>,
}

export function createChunkTextSelectionState(
  { current, textHighlight }: CreateChunkTextSelectionStateParams
) {
  const [value, setValue] =
    createSignal<ChunkTextSelection | undefined>(undefined, {
      equals: (newVal, oldVal) =>
        newVal === oldVal
        || ((newVal && oldVal && chunkTextSelectionEquals(newVal, oldVal)) ?? false)
    });

  const inProgress = (): boolean => value()?.anchor !== undefined;

  function finish() {
    setValue(curr => {
      if (curr) {
        curr.anchor = undefined;
      }
      return curr;
    });
  }

  function isWholeTextSelected(): boolean {
    const sel = value();
    if (!sel) {
      return false;
    }
    const [start, end] = sel.range;
    return start === 0 && end === current().text.length - 1;
  }

  function selectAll() {
    setValue({ range: [0, current().text.length - 1] });
  }

  function selectHighlighted() {
    const hl = textHighlight();
    if (!hl) {
      return;
    }
    setValue({ range: hl });
  }

  return {
    get: value,
    set: setValue,

    inProgress,

    finish,
    isWholeTextSelected,
    selectAll,
    selectHighlighted,
  };
}
