import { createEffect, type VoidComponent } from "solid-js";

import { YomichanSentenceDelimiter } from "~/common";

import type { ChunksState } from "./ChunksState";
import { ChunkLabel } from "./Label";

interface ChunkLabelWrapperProps {
  chunksState: ChunksState,
}

export const ChunkLabelWrapper: VoidComponent<ChunkLabelWrapperProps> = (props) => {
  let label: ChunkLabel;

  createEffect(() => label.setChunk(props.chunksState.current()));

  createEffect(() => label.setSelection(props.chunksState.textSelection.get()));

  createEffect(() => label.setHighlight(props.chunksState.textHighlight()));

  createEffect(() => label.setFlash(props.chunksState.currentFlashState()));

  return <div lang="ja" ref={el => label = new ChunkLabel(el)}>
    <YomichanSentenceDelimiter/>
  </div>;
};
