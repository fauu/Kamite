import { createEffect, createSelector, createSignal, on } from "solid-js";
import { createStore } from "solid-js/store";

import type { Config, LookupTarget } from "~/backend";
import type { ChunksState } from "~/chunk";
import { getClientHeight } from "~/common";
import { getSetting, type Setting } from "~/settings";

import {
  notebookHeightZero, notebookHeightFromPercent, notebookHeightFromPx, type NotebookHeight
} from "./Height";
import { BASE_NOTEBOOK_TABS, lookupTargetSymbolToLookupTabID, type NotebookTab } from "./tabs";

export const MIN_HEIGHT_PERCENT = 0.25;
export const MAX_HEIGHT_PERCENT = 0.90;
export const INITIAL_HEIGHT = notebookHeightFromPercent(0.5);
export const DEADZONE_PX = 25;

type LookupOverride = {
  text?: string,
  symbol: string,
};

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
  const [collapsed, setCollapsed] =
    createSignal(false);
  const [lookupOverride, setLookupOverride] =
    createSignal<LookupOverride | undefined>(undefined);

  let mainSectionEl: HTMLDivElement;

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

  createEffect(on(collapsed, collapsed => {
    syncMainSectionElHeight(collapsed ? notebookHeightZero() : height());
  }));

  function setMainSectionEl(el: HTMLDivElement) {
    mainSectionEl = el;
  }

  function syncHeight(config?: Config) {
    let configHeight = config?.ui.notebook.height;
    if (configHeight) {
      configHeight /= 100;
    }
    resizeToPercent(configHeight || height().percent);
  }

  function resizeByPx(deltaY: number) {
    resizeToPx(height().px + deltaY);
  }

  function resizeToPercent(percent: number) {
    resizeToPx(notebookHeightFromPercent(percent).px);
  }

  function resizeToPx(px: number) {
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

    if (!collapsed()) {
      syncMainSectionElHeight(targetHeight);
    }
  }

  function setTabHidden(id: typeof BASE_NOTEBOOK_TABS[number]["id"], hidden: boolean) {
    setTabs(t => t.id === id, "hidden", hidden);
  }

  function activateTab(id: typeof BASE_NOTEBOOK_TABS[number]["id"]) {
    const idx = tabs.findIndex(t => t.id === id);
    if (idx !== undefined) {
      setActiveTabIdx(idx);
      setCollapsed(false);
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

  function isCollapseAllowed(settings: Setting[]) {
    return !resizing() && !collapsed() && (getSetting(settings, "notebook-collapse") || false);
  }

  function maybeCollapse(isFlipped: boolean, mouseY: number, movementY?: number) {
    // If `movementY` is provided, collapse only when the cursor has just been moved outisde.
    // Otherwise, collapse simply if the cursor is currently outside.
    const previouslyInsideCondition =
      (movementY === undefined)
      || !isCursorDecidedlyOutside(isFlipped, mouseY - movementY);
    const currentlyOutside = isCursorDecidedlyOutside(isFlipped, mouseY);
    if (currentlyOutside && previouslyInsideCondition) {
      setCollapsed(true);
    }
  }

  function resizeMaybeStart(isFlipped: boolean, mouseY: number) {
    const resizeHandleStart = outerEdgeY(isFlipped);
    const withinResizeHandle = mouseY >= resizeHandleStart - 3 && mouseY <= resizeHandleStart + 2;
    if (withinResizeHandle) {
      setResizing(true);
    }
  }

  function resizeTick(isFlipped: boolean, yMovement: number) {
    const sign = !isFlipped ? -1 : 1;
    resizeByPx(sign * yMovement);
  }

  function syncMainSectionElHeight(targetNotebookHeight: NotebookHeight) {
    const msHeight = `${getClientHeight() - targetNotebookHeight.px}px`;
    mainSectionEl.style.maxHeight = msHeight;
    mainSectionEl.style.flexBasis = msHeight;
  }

  function outerEdgeY(isFlipped: boolean): number {
    return !isFlipped ? mainSectionEl.offsetHeight : height().px;
  }

  function isCursorDecidedlyOutside(isFlipped: boolean, y: number): boolean {
    const flipSign = !isFlipped ? -1 : 1;
    const effectiveOuterEdgeY = outerEdgeY(isFlipped) + (flipSign * DEADZONE_PX);
    return flipSign * y > flipSign * effectiveOuterEdgeY;
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
    collapsed,
    setCollapsed,
    lookupOverride,
    setLookupOverride,

    groupedTabs,
    activeTab,
    isTabActive,

    setMainSectionEl,
    syncHeight,
    resizeByPx,
    setTabHidden,
    activateTab,
    updateLookupTabs,
    getLookupTabs,
    isCollapseAllowed,
    maybeCollapse,
    resizeMaybeStart,
    resizeTick,
  };
}
