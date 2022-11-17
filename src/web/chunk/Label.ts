import { css } from "solid-styled-components";

import { BgFlashingClass, ChromeClass } from "~/style";

import type { Chunk } from "./Chunk";
import type { ChunkFlashState } from "./ChunksState";
import { ChunkTextClass } from "./TextClass";
import type { ChunkTextSelection } from "./TextSelectionState";

export const CHUNK_LABEL_ID = "chunk-label";
export const CHUNK_CHAR_IDX_ATTR_NAME = "charIdx";

const RUBY_TEXT_SCALE_PROP_NAME = "--ruby-text-scale";
const DEFAULT_RUBY_TEXT_SCALE = 1;

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

          // Scale ruby text down if necessary to avoid overflow
          const rubyCharsPerBaseChar = maybeRuby.text.length / maybeRuby.base.length;
          const rubyTextScale = this.#rubyTextScale(rubyCharsPerBaseChar);
          if (rubyTextScale !== DEFAULT_RUBY_TEXT_SCALE) {
            rtEl.style.setProperty(RUBY_TEXT_SCALE_PROP_NAME, rubyTextScale.toString());
          }

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
    this.#rootEl.classList.toggle(HasSelectionClass, selection !== undefined);
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

  #rubyTextScale(rubyCharsPerBaseChar: number): number {
    if (rubyCharsPerBaseChar <= 2) {
      return 1;
    } else if (rubyCharsPerBaseChar < 3) {
      return 0.85;
    } else if (rubyCharsPerBaseChar < 4) {
      return 0.7;
    } else {
      return 0.55;
    }
  }
}

const RootClass = css`
  ${RUBY_TEXT_SCALE_PROP_NAME}: ${DEFAULT_RUBY_TEXT_SCALE.toString()};
  --ruby-text-font-size-base: calc(0.5 * var(--chunk-font-size) * var(--chunk-furigana-font-scale));

  box-sizing: content-box;
  margin-top: 0.3rem;

  .${ChromeClass} & {
    line-height: 1.3;

    ruby {
      line-height: 1.8;
    }
  }

  rt {
    font-size: calc(var(${RUBY_TEXT_SCALE_PROP_NAME}) * var(--ruby-text-font-size-base));
    font-weight: var(--chunk-furigana-font-weight);
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

const HasSelectionClass = css`
  --unselected-char-color: var(--color-fg4);

  .${CharClass}:not(.${SelectedCharClass}) {
    color: var(--unselected-char-color);

    & + rt {
      color: var(--unselected-char-color);
    }
  }
`;

const HighlightedCharClass = css`
  background: var(--color-accB2);
`;
