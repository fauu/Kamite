import { css } from "solid-styled-components";

import { BgFlashingClass, ChromeClass } from "~/style";

import type { Chunk } from "./Chunk";
import type { ChunkFlashState } from "./ChunksState";
import { ChunkTextClass } from "./TextClass";
import type { ChunkTextSelection } from "./TextSelectionState";

export const CHUNK_LABEL_ID = "chunk-label";
export const CHUNK_CHAR_IDX_ATTR_NAME = "charIdx";

export class ChunkLabel {
  #rootEl: HTMLDivElement;
  #subRootEl: HTMLSpanElement;

  constructor(rootEl: HTMLDivElement) {
    this.#rootEl = rootEl;
    rootEl.classList.add(RootClass, ChunkTextClass);
    this.#subRootEl = document.createElement("span");
    this.#subRootEl.id = CHUNK_LABEL_ID;
    this.#rootEl.prepend(this.#subRootEl);
  }

  setChunk(chunk: Chunk) {
    const newLabelChildren: HTMLElement[] = [];
    let idx = 0;
    if (chunk.text.hasFurigana) {
      for (const maybeRuby of chunk.text.maybeRubies!) {
        if (!maybeRuby.text) {
          for (const ch of maybeRuby.base) {
            newLabelChildren.push(this.#makeCharElement(ch, idx++));
          }
        } else {
          const rubyEl = document.createElement("ruby");
          const rubyElChildren: HTMLElement[] = [];
          for (const ch of maybeRuby.base) {
            rubyElChildren.push(this.#makeCharElement(ch, idx++));
          }
          const rtEl = document.createElement("rt");
          rtEl.textContent = maybeRuby.text;
          rubyElChildren.push(rtEl);
          rubyEl.replaceChildren(...rubyElChildren);
          newLabelChildren.push(rubyEl);
        }
      }
    } else {
      for (const ch of chunk.text.base) {
        newLabelChildren.push(this.#makeCharElement(ch, idx++));
      }
    }
    this.#subRootEl.replaceChildren(...newLabelChildren);
  }

  setSelection(selection: ChunkTextSelection | undefined) {
    this.#setCharElementsClassByRange(SelectedCharClass, selection?.range);
  }

  setHighlight(highlight: [number, number] | undefined) {
    this.#setCharElementsClassByRange(HighlightedCharClass, highlight);
  }

  setFlash(flashState: ChunkFlashState) {
    let newFlashingRange: [number, number] | undefined;
    switch (flashState.kind) {
      case "not-flashing":
        this.#subRootEl.classList.remove(BgFlashingClass);
        break;
      case "whole-flashing":
        this.#subRootEl.classList.add(BgFlashingClass);
        break;
      case "range-flashing":
        newFlashingRange = flashState.range;
        break;
    }
    this.#setCharElementsClassByRange(BgFlashingClass, newFlashingRange);
  }

  #makeCharElement(ch: string, idx: number) {
    let el: HTMLElement;
    if (ch === "\n") {
      el = document.createElement("br");
    } else {
      el = document.createElement("span");
      el.classList.add(CharClass);
      el.textContent = ch;
    }
    el.dataset[CHUNK_CHAR_IDX_ATTR_NAME] = idx.toString();
    return el;
  }

  #forCharElement(fn: (charEl: HTMLElement, rawCharIdx: string, ...restArgs: any) => void) {
    for (const labelChildAbstractEl of this.#subRootEl.children) {
      const labelChildEl = labelChildAbstractEl as HTMLElement;
      const rawCharIdx = labelChildEl.dataset[CHUNK_CHAR_IDX_ATTR_NAME];
      if (rawCharIdx) {
        fn(labelChildEl, rawCharIdx);
      } else {
        for (const rubyChildAbstractEl of labelChildEl.children) {
          const rubyChildEl = rubyChildAbstractEl as HTMLElement;
          const rawCharIdxRuby = rubyChildEl.dataset[CHUNK_CHAR_IDX_ATTR_NAME];
          rawCharIdxRuby && fn(rubyChildEl, rawCharIdxRuby);
        }
      }
    }
  }

  #setCharElementClassByRange(
    charEl: HTMLElement,
    rawCharIdx: string,
    className: string,
    range: [number, number] | undefined
  ) {
    let op: "add" | "remove" | undefined;
    if (!range) {
      op = "remove";
    } else {
      const idx = parseInt(rawCharIdx);
      const inRange = idx >= range[0] && idx <= range[1];
      op = inRange ? "add" : "remove";
    }
    charEl.classList[op](className);
  }

  #setCharElementsClassByRange(className: string, range: [number, number] | undefined) {
    this.#forCharElement((charEl, rawCharIdx) =>
      this.#setCharElementClassByRange(charEl, rawCharIdx, className, range)
    );
  }
}

const RootClass = css`
  box-sizing: content-box;
  margin-top: 0.3rem;

  .${ChromeClass} & {
    line-height: 1.39;

    ruby {
      line-height: 1.85;
    }
  }

  rt {
    font-size: 1.18rem;
    margin-bottom: -0.1em;
  }

  ::selection {
    background: transparent;
  }
`;

const CharClass = css`
  &:hover {
    border-bottom: 2px dotted var(--color-accC);
  }
`;

const SelectedCharClass = css`
  border-bottom: 2px solid var(--color-accB) !important;
`;

const HighlightedCharClass = css`
  background: var(--color-accB2);
`;
