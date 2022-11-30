import { css } from "solid-styled-components";

import { ChunkCharIdxAttrName, ChunkLabelId, RootId } from "~/dom";
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

  #selectionStartMarkerEl: HTMLDivElement;
  #selectionEndMarkerEl: HTMLDivElement;

  constructor(rootEl: HTMLDivElement) {
    this.#rootEl = rootEl;
    rootEl.classList.add(RootClass, ChunkTextClass);
    this.#subRootEl = document.createElement("span");
    this.#subRootEl.id = ChunkLabelId;
    this.#rootEl.prepend(this.#subRootEl);

    this.#selectionStartMarkerEl = document.createElement("div");
    this.#selectionStartMarkerEl.classList.add(SelectionStartMarkerClass);
    this.#rootEl.append(this.#selectionStartMarkerEl);
    this.#selectionEndMarkerEl = document.createElement("div");
    this.#selectionEndMarkerEl.classList.add(SelectionEndMarkerClass);
    this.#rootEl.append(this.#selectionEndMarkerEl);
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
          // All <rt>s must have the same `font-size` so that the line height of the annotations is
          // consistent. But we still want to have different font sizes, hence the additional inner
          // <span>.
          const rtProperEl = document.createElement("span");
          rtProperEl.textContent = maybeRuby.text;
          rtEl.append(rtProperEl);
          rubyElChildren.push(rtEl);
          rubyEl.replaceChildren(...rubyElChildren);

          // Scale ruby text down if necessary to avoid inconsistent letter spacing
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
    this.#rootEl.classList.toggle(HasSelectionClass, selection !== undefined);
    this.#setCharElementsClassByRange(SelectedCharClass, selection?.range);
    if (selection) {
      const [start, end] = selection.range;
      const startStr = start.toString();
      const endStr = end.toString();
      let foundStart = false;
      console.log(startStr, endStr);
      this.#forCharElement((charEl, rawCharIdx) => {
        if (!foundStart && rawCharIdx === startStr) {
          foundStart = true;
          const rect = charEl.getBoundingClientRect();
          const [x, y, height] = [rect.left, rect.y, rect.height];
          this.#selectionStartMarkerEl.style.left = x.toString() + "px";
          this.#selectionStartMarkerEl.style.top = y.toString() + "px";
          // PERF: Should be set once
          this.#selectionStartMarkerEl.style.height = height.toString() + "px";
          this.#selectionStartMarkerEl.style.display = "block";
          console.log(this.#selectionStartMarkerEl);
        }
        if (foundStart && rawCharIdx === endStr) {
          // XXX: (DRY)
          const rect = charEl.getBoundingClientRect();
          const [x, y, height] = [rect.right, rect.y, rect.height];
          this.#selectionEndMarkerEl.style.left = (x - 8).toString() + "px"; // XXX: Hardcoded
          this.#selectionEndMarkerEl.style.top = y.toString() + "px";
          this.#selectionEndMarkerEl.style.height = height.toString() + "px";
          this.#selectionEndMarkerEl.style.display = "block";
          return false;
        }
      });
    } else {
      this.#selectionStartMarkerEl.style.display = "none";
      this.#selectionEndMarkerEl.style.display = "none";
    }
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

  #forCharElement(fn: (charEl: HTMLElement, rawCharIdx: string, ...restArgs: any) => void | false) {
    for (const labelChildAbstractEl of this.#subRootEl.children) {
      const labelChildEl = labelChildAbstractEl as HTMLElement;
      const rawCharIdx = labelChildEl.dataset[ChunkCharIdxAttrName];
      if (rawCharIdx) {
        if (fn(labelChildEl, rawCharIdx) === false) {
          break;
        }
      } else {
        for (const rubyChildAbstractEl of labelChildEl.children) {
          const rubyChildEl = rubyChildAbstractEl as HTMLElement;
          const rawCharIdxRuby = rubyChildEl.dataset[ChunkCharIdxAttrName];
          if (rawCharIdxRuby) {
            if (fn(rubyChildEl, rawCharIdxRuby) === false) {
              break;
            }
          }
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
      line-height: 1.7;
    }
  }

  rt {
    font-size: var(--ruby-text-font-size-base);
    margin-bottom: -0.25em;
    .${ChromeClass} & {
      margin-bottom: -0.17em;
    }

    & > span {
      font-size: calc(1em * var(${RUBY_TEXT_SCALE_PROP_NAME}));
      font-weight: var(--chunk-furigana-font-weight);
    }
  }

  ::selection {
    background: transparent;
  }
`;

const RubyTextConcealedClass = css`
  ruby {
    position: relative;
    z-index: 1;
  }

  ruby:not(:hover):before {
    width: calc(100% + 0.1rem);
    height: calc(var(--ruby-text-font-size-base) + 0.3rem);
    content: "";
    position: absolute;
    top: calc(-1 * var(--ruby-text-font-size-base) - 2px);
    left: calc(0rem - 0.05rem);
    filter: blur(1px);
    background: var(--color-bg2-hl);
    z-index: 2;
  }
`;

const CharClass = css`
  position: relative;
  z-index: 3;

  &:hover {
    .${ChromeClass} & {
      text-underline-offset: 3px;
    }
    text-decoration: underline;
    text-decoration-style: dotted;
    text-decoration-thickness: 2px;
    text-decoration-color: var(--color-accC);
  }
`;

const SelectedCharClass = css`
  background: var(--color-bg2);
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
  z-index: 5;
`;

const SelectionStartMarkerClass = css`
  width: 8px;
  position: fixed;
  border: 2px solid var(--color-accB);
  border-right: none;
  display: none;
  z-index: 10;
`;

const SelectionEndMarkerClass = css`
  width: 8px;
  position: fixed;
  border: 2px solid var(--color-accB);
  border-left: none;
  display: none;
  z-index: 10;
`;

