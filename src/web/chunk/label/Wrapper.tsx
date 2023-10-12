import { createEffect, on, type Accessor, type VoidComponent } from "solid-js";

import type { ChunksState } from "~/chunk";
import {
  debounce, LAYOUT_SHIFT_HANDLER_DEBOUNCE_DEFAULT_MS, YomichanSentenceDelimiter
} from "~/common";

import { ChunkLabel } from "./Label";

interface ChunkLabelWrapperProps {
  chunksState: ChunksState,
  movingMouseWhilePrimaryDown: Accessor<boolean>,
  selectionAutoHighlight: Accessor<boolean>,
}

export const ChunkLabelWrapper: VoidComponent<ChunkLabelWrapperProps> = (props) => {
  let label: ChunkLabel;

  createEffect(() => label.setChunk(props.chunksState.current()));

  createEffect(on(props.chunksState.textSelection.get, (selection) => {
    label.setSelection(selection);
    if (selection) {
      label.highlightRange(selection.range);
    }
  }));

  createEffect(() => label.setHighlight(props.chunksState.textHighlight()));

  createEffect(() => label.setFlash(props.chunksState.currentFlashState()));

  const handleRootRef = (el: HTMLDivElement) => {
    label = new ChunkLabel(el, props.chunksState.concealRubies, props.movingMouseWhilePrimaryDown);

    new ResizeObserver(debounce(() => {
      // PERF: Selection start and end elements remain constant here, so we don't need to recreate the
      //       entire selection UI here, just shift their positions
      label.setSelection(props.chunksState.textSelection.get());
    }, LAYOUT_SHIFT_HANDLER_DEBOUNCE_DEFAULT_MS)).observe(el);
  };

  return <div lang="ja" ref={handleRootRef}>
    <YomichanSentenceDelimiter/>
  </div>;
};
