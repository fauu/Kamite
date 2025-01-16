import type { LookupTarget } from "~/backend";

export type NotebookTab = {
  readonly id: string,
  readonly symbol?: string,
  readonly title: string,
  readonly lookup?: LookupTarget,
  highlighted?: boolean,
  hidden?: boolean,
  readonly keepScrolled?: boolean,
  readonly group: number,
};

export function notebookTabIsEmbeddedLookup(tab: NotebookTab) {
  return tab.lookup && !tab.lookup.newTab;
}

export function lookupTargetSymbolToLookupTabID(n: LookupTarget["symbol"]) {
  return `lookup-${n}`;
}

export const BASE_NOTEBOOK_TABS: NotebookTab[] = [
  {
    id: "chunk-history",
    title: "History",
    keepScrolled: true,
    group: 1,
  },
  {
    id: "settings",
    title: "Settings",
    group: 1,
  },
  {
    id: "debug",
    title: "Debug",
    keepScrolled: true,
    group: 1,
  },
];
