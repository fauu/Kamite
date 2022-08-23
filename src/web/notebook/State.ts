import { createEffect, createSelector, createSignal } from "solid-js";
import { createStore } from "solid-js/store";

import type { Config, LookupTarget } from "~/backend";
import type { ChunksState } from "~/chunk";
import { getClientHeight } from "~/common";

import { notebookHeightFromPercent, notebookHeightFromPx } from "./Height";
import { BASE_NOTEBOOK_TABS, lookupTargetSymbolToLookupTabID, type NotebookTab } from "./tabs";

export const MIN_HEIGHT_PERCENT = 0.25;
export const MAX_HEIGHT_PERCENT = 0.75;
export const INITIAL_HEIGHT = notebookHeightFromPercent(0.5);

type LookupOverride = {
  text?: string,
  symbol: string,
}

export type NotebookState = ReturnType<typeof createNotebookState>;

interface CreateNotebookStateParams {
  chunks: ChunksState,
}

export function createNotebookState({ chunks }: CreateNotebookStateParams) {
  const [tabs, setTabs] =
    createStore([...BASE_NOTEBOOK_TABS]);
  const [activeTabIdx, setActiveTabIdx] =
    createSignal(tabs.findIndex(t => t.id === import.meta.env.VITE_DEFAULT_TAB)!);
  const [height, setHeight] =
    createSignal(INITIAL_HEIGHT);
  const [resizing, setResizing] =
    createSignal(false);
  const [lookupOverride, setLookupOverride] =
    createSignal<LookupOverride | undefined>(undefined);

  const groupedTabs = (): NotebookTab[][] =>
    tabs.reduce((acc, tab) => {
      if (!acc[tab.group]) {
        acc[tab.group] = [] as NotebookTab[];
      }
      acc[tab.group].push(tab);
      return acc;
    }, [] as NotebookTab[][]);

  const activeTab = (): NotebookTab => tabs[activeTabIdx()];
  const activeTabId = (): NotebookTab["id"] => activeTab().id;
  const isTabActive = createSelector(activeTabId);

  let lookupTabs: NotebookTab[];

  createEffect(() => {
    setTabs(
      tabs.findIndex(t => t.id === "chunk-history"),
      "highlighted",
      chunks.selectionInfo().otherThanCurrentSelected
    );
  });

  function syncHeight(mainSectionEl: HTMLDivElement, config?: Config) {
    let configHeight = config?.ui.notebook.height;
    if (configHeight) {
      configHeight /= 100;
    }
    resizeToPercent(configHeight || height().percent, mainSectionEl);
  }

  function resizeByPx(deltaY: number, mainSectionEl: HTMLDivElement) {
    resizeToPx(height().px + deltaY, mainSectionEl);
  }

  function resizeToPercent(percent: number, mainSectionEl: HTMLDivElement) {
    const h = notebookHeightFromPercent(percent);
    resizeToPx(h.px, mainSectionEl);
  }

  function resizeToPx(px: number, mainSectionEl: HTMLDivElement) {
    if (px === 0) {
      // Needed to fix a bug where resizing is attempted when the height isn't
      // properly initialized due to the browser tab being opened off screen
      return;
    }

    let targetHeight = notebookHeightFromPx(px);

    let newPercent;
    if (targetHeight.percent < MIN_HEIGHT_PERCENT) {
      newPercent = MIN_HEIGHT_PERCENT;
    } else if (targetHeight.percent > MAX_HEIGHT_PERCENT) {
      newPercent = MAX_HEIGHT_PERCENT;
    }
    if (newPercent) {
      targetHeight = notebookHeightFromPercent(newPercent);
    }

    setHeight(targetHeight);

    const msHeight = `${getClientHeight() - targetHeight.px}px`;
    mainSectionEl.style.maxHeight = msHeight;
    mainSectionEl.style.flexBasis = msHeight;
  }

  function setTabHidden(id: typeof BASE_NOTEBOOK_TABS[number]["id"], hidden: boolean) {
    setTabs(t => t.id === id, "hidden", hidden);
  }

  function activateTab(id: typeof BASE_NOTEBOOK_TABS[number]["id"]) {
    const idx = tabs.findIndex(t => t.id === id);
    if (idx !== undefined) {
      setActiveTabIdx(idx);
    }
  }

  function updateLookupTabs(lookupTargets: LookupTarget[]) {
    lookupTabs = lookupTargets.map(t => ({
      id: lookupTargetSymbolToLookupTabID(t.symbol),
      title: t.name,
      symbol: t.symbol,
      lookup: t,
      group: 0,
    }));
    setTabs([...BASE_NOTEBOOK_TABS, ...lookupTabs]);
  }

  function getLookupTabs(): readonly NotebookTab[] {
    return lookupTabs;
  }

  return {
    tabs,
    // setTabs,
    activeTabIdx,
    setActiveTabIdx,
    height,
    // setHeight,
    resizing,
    setResizing,
    lookupOverride,
    setLookupOverride,

    groupedTabs,
    activeTab,
    isTabActive,

    syncHeight,
    resizeByPx,
    setTabHidden,
    activateTab,
    updateLookupTabs,
    getLookupTabs,
  };
}
