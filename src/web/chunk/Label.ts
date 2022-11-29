import { css } from "solid-styled-components";

import { ChunkCharIdxAttrName, ChunkLabelId } from "~/dom";
import { BgFlashingClass, ChromeClass } from "~/style";

import type { Chunk } from "./Chunk";
import type { ChunkFlashState } from "./ChunksState";
import { ChunkTextClass } from "./TextClass";
import type { ChunkTextSelection } from "./TextSelectionState";

const RUBY_TEXT_SCALE_PROP_NAME = "--ruby-text-scale";
const DEFAULT_RUBY_TEXT_SCALE = 1;

export class ChunkLabel {
  #rootEl: HTMLDivElement;
  #subRootEl: HTMLSpanElement;

  constructor(rootEl: HTMLDivElement) {
    this.#rootEl = rootEl;
    rootEl.classList.add(RootClass, ChunkTextClass);
    this.#subRootEl = document.createElement("span");
    this.#subRootEl.id = ChunkLabelId;
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

  setRubyConcealed(rubyConcealed: boolean) {
    this.#rootEl.classList.toggle(RubyTextConcealedClass, rubyConcealed);
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
    el.dataset[ChunkCharIdxAttrName] = idx.toString();
    return el;
  }

  #forCharElement(fn: (charEl: HTMLElement, rawCharIdx: string, ...restArgs: any) => void) {
    for (const labelChildAbstractEl of this.#subRootEl.children) {
      const labelChildEl = labelChildAbstractEl as HTMLElement;
      const rawCharIdx = labelChildEl.dataset[ChunkCharIdxAttrName];
      if (rawCharIdx) {
        fn(labelChildEl, rawCharIdx);
      } else {
        for (const rubyChildAbstractEl of labelChildEl.children) {
          const rubyChildEl = rubyChildAbstractEl as HTMLElement;
          const rawCharIdxRuby = rubyChildEl.dataset[ChunkCharIdxAttrName];
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
      return 0.8;
    } else if (rubyCharsPerBaseChar < 4) {
      return 0.65;
    } else {
      return 0.5;
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
    font-size: calc(var(--ruby-text-font-size-base) * var(${RUBY_TEXT_SCALE_PROP_NAME}));
    font-weight: var(--chunk-furigana-font-weight);
    margin-bottom: -0.2em;
  }

  ::selection {
    background: transparent;
  }
`;

const RubyTextConcealedClass = css`
  ruby {
    position: relative;
  }

  ruby:not(:hover)::before {
    width: calc(100% + 0.08rem);
    height: calc(var(--ruby-text-font-size-base) + 0.23rem);
    content: "";
    position: absolute;
    top: calc(-1 * var(--ruby-text-font-size-base) - 1px);
    left: calc(0rem - 0.04rem);
    filter: blur(1px);
    background: var(--color-bg2-hl);
  }

`;

const CharClass = css`
  text-underline-offset: 0.1em;

  &:hover {
    text-decoration: underline;
    text-decoration-style: dotted;
    text-decoration-thickness: 2px;
    text-decoration-color: var(--color-accC);
  }
`;

const SelectedCharClass = css`
  text-decoration: underline;
  text-decoration-style: solid !important;
  text-decoration-thickness: 2px;
  text-decoration-color: var(--color-accB) !important;
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
