import { Accessor, createEffect, type VoidComponent } from "solid-js";

import { YomichanSentenceDelimiter } from "~/common";

import type { ChunksState } from "./ChunksState";
import { ChunkLabel } from "./Label";

interface ChunkLabelWrapperProps {
  chunksState: ChunksState,
  movingMouseWhilePrimaryDown: Accessor<boolean>,
}

export const ChunkLabelWrapper: VoidComponent<ChunkLabelWrapperProps> = (props) => {
  let label: ChunkLabel;

  createEffect(() => label.setChunk(props.chunksState.current()));

  createEffect(() => label.setRubyConcealed(props.chunksState.rubyConcealed()));

  createEffect(() => label.setSelection(props.chunksState.textSelection.get()));

  createEffect(() => label.setHighlight(props.chunksState.textHighlight()));

  createEffect(() => label.setFlash(props.chunksState.currentFlashState()));

  return <div lang="ja" ref={el => label = new ChunkLabel(el, props.movingMouseWhilePrimaryDown)}>
    <YomichanSentenceDelimiter/>
  </div>;
};
