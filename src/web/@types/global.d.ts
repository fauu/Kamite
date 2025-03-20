import type { Accessor } from "solid-js";

export {};

declare global {
  interface Window {
    root: HTMLDivElement,
    mainUIVisible: Accessor<boolean>,
  }

  interface Range {
    // E.g. `true` for highlight resulting from user chunk selection, `false` for highlight
    // resulting from Yomichan hover
    fromKamiteChunkAction?: boolean,
  }
}
