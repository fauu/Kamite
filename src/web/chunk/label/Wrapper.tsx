import { createEffect, type Accessor, type VoidComponent } from "solid-js";

import type { ChunksState } from "~/chunk";
import { YomichanSentenceDelimiter } from "~/common";

import { ChunkLabel } from "./Label";

interface ChunkLabelWrapperProps {
  chunksState: ChunksState,
  movingMouseWhilePrimaryDown: Accessor<boolean>,
}

export const ChunkLabelWrapper: VoidComponent<ChunkLabelWrapperProps> = (props) => {
  let label: ChunkLabel;

  createEffect(() => label.setChunk(props.chunksState.current()));

  createEffect(() => label.setSelection(props.chunksState.textSelection.get()));

  createEffect(() => label.setHighlight(props.chunksState.textHighlight()));

  createEffect(() => label.setFlash(props.chunksState.currentFlashState()));

  return <div
    lang="ja"
    ref={el =>
      label = new ChunkLabel(el, props.chunksState.concealRubies, props.movingMouseWhilePrimaryDown)
    }
  >
    <YomichanSentenceDelimiter/>
  </div>;
};
