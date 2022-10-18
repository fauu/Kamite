import { createEffect, createSignal, on } from "solid-js";

import type { ChunksState } from "~/chunk";
import { createEmptySignal, domRectsOverlap } from "~/common";
import type { NotebookState } from "~/notebook";

interface CreateStatusPanelFaderParams {
  chunkLabelAndTranslationEl: () => HTMLDivElement,
  statusPanelEl: () => HTMLDivElement,
  notebook: NotebookState,
  chunks: ChunksState,
}

export function createStatusPanelFader(params: CreateStatusPanelFaderParams) {
  const [shouldFade, setShouldFade] =
    createSignal(false);
  const [fadeInvalidated, setFadeInvalidated] =
    createEmptySignal();

  createEffect(on(
    [
      // QUAL: The two should possibly be combined into something like `effectiveHeight`, since in
      //       reality collapsing/expanding just means changing the height, and it's only this
      //       `effectiveHeight` that determines status panel fading
      params.notebook.height,
      params.notebook.collapsed,

      params.chunks.current,
      params.chunks.translationWithContext,
    ],
    setFadeInvalidated
  ));

  createEffect(on(fadeInvalidated, () =>
    // PERF: Could debounce
    setShouldFade(
      domRectsOverlap(
        params.chunkLabelAndTranslationEl().getBoundingClientRect(),
        params.statusPanelEl().getBoundingClientRect(),
      )
    )
  ));

  return { shouldFade };
}
