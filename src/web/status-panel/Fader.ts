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

  createEffect(on(params.notebook.height, setFadeInvalidated));
  createEffect(on(params.chunks.current, setFadeInvalidated));
  createEffect(on(params.chunks.translationWithContext, setFadeInvalidated));

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
