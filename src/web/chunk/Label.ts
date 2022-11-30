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
  #rubyEls: HTMLElement[] = [];
  #rubyConcealed = false;
  #initialRubyConcealPending = false;

  #selectionMarkerEls: [HTMLDivElement, HTMLDivElement];

  constructor(rootEl: HTMLDivElement) {
    // Base init
    this.#rootEl = rootEl;
    rootEl.classList.add(RootClass, ChunkTextClass);
    this.#subRootEl = document.createElement("span");
    this.#subRootEl.id = ChunkLabelId;
    this.#rootEl.prepend(this.#subRootEl);

    // Selection marker init
    const start = document.createElement("div");
    start.classList.add(SelectionMarkerClass, SelectionStartMarkerClass);
    const end = document.createElement("div");
    end.classList.add(SelectionMarkerClass, SelectionEndMarkerClass);
    this.#selectionMarkerEls = [start, end];
    this.#rootEl.append(...this.#selectionMarkerEls);
  }

  setChunk(chunk: Chunk) {
    const newLabelChildren: HTMLElement[] = [];
    this.#rubyEls = [];
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
          this.#rubyEls.push(rubyEl);

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

    if (this.#initialRubyConcealPending) {
      this.setRubyConcealed(true);

      for (const el of this.#rubyEls) {
        el.addEventListener("mouseover", event => this.#handleRubyHoverStateChange(event, true));
        el.addEventListener("mouseout",  event => this.#handleRubyHoverStateChange(event, false));
      }
    }
  }

  #handleRubyHoverStateChange(event: MouseEvent, hover: boolean) {
    const targetRubyEl = event.currentTarget as HTMLElement;
    if (!this.#rubyConcealed) {
      return;
    }
    for (const el of this.#rubyEls) {
      el.classList.toggle(RubyTextConcealedClass, !hover);
      if (el === targetRubyEl) {
        break;
      }
    }
  }

  setRubyConcealed(rubyConcealed: boolean) {
    this.#rubyConcealed = rubyConcealed;
    if (rubyConcealed && this.#rubyEls.length === 0) {
      // <ruby> elements haven't been created yet. Call this function again after this has been done
      this.#initialRubyConcealPending = true;
      return;
    }
    for (const el of this.#rubyEls) {
      el.classList.toggle(RubyTextConcealedClass, rubyConcealed);
    }
  }

  setSelection(selection: ChunkTextSelection | undefined) {
    this.#rootEl.classList.toggle(HasSelectionClass, selection !== undefined);
    this.#setCharElementsClassByRange(SelectedCharClass, selection?.range);
    if (selection) {
      const [start, end] = selection.range;
      const startStr = start.toString();
      const endStr = end.toString();
      let foundStart = false;
      this.#forCharElement((charEl, rawCharIdx) => {
        charEl.classList.toggle(SelectionStartCharClass, false);
        charEl.classList.toggle(SelectionEndCharClass, false);
        let rect: DOMRect | undefined = undefined;
        if (!foundStart && rawCharIdx === startStr) {
          rect = charEl.getBoundingClientRect();
          foundStart = true;
          this.#selectionMarkerEls[0].style.setProperty("--x", rect.left.toString());
          this.#selectionMarkerEls[0].style.setProperty("--y", rect.y.toString());
          this.#selectionMarkerEls[0].style.display = "block";
          charEl.classList.toggle(SelectionStartCharClass, true);

          // PERF: Should be set once
          this.#rootEl.style.setProperty("--char-height", rect.height.toString());
        }
        if (foundStart && rawCharIdx === endStr) {
          if (!rect) {
            rect = charEl.getBoundingClientRect();
          }
          // XXX: (DRY)
          this.#selectionMarkerEls[1].style.setProperty("--x", rect.right.toString());
          this.#selectionMarkerEls[1].style.setProperty("--y", rect.y.toString());
          this.#selectionMarkerEls[1].style.display = "block";
          charEl.classList.toggle(SelectionEndCharClass, true);
          return false;
        }
      });
    } else {
      this.#selectionMarkerEls.forEach(el => el.style.display = "none");
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
          if (rawCharIdxRuby && fn(rubyChildEl, rawCharIdxRuby) === false) {
            break;
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
  --selection-border-radius: 2px;

  box-sizing: content-box;
  margin-top: var(--text-margin-top);

  .${ChromeClass} & {
    ruby {
      line-height: calc(var(--line-height) * 1.25);
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
  position: relative;
  z-index: 1;

  &:before {
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
  background: linear-gradient(0, var(--color-bg3) 0%, transparent 100%);
`;

const SelectionStartCharClass = css`
  border-bottom-left-radius: var(--selection-border-radius);
`;

const SelectionEndCharClass = css`
  border-bottom-right-radius: var(--selection-border-radius);
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

const SelectionMarkerClass = css`
  --width: calc((var(--char-height) * 0.25) * 1px);
  --height: calc((var(--char-height) * 0.5) * 1px);
  --yPx: calc(var(--y) * 1px);
  --xPx: calc(var(--x) * 1px);

  width: var(--width);
  height: var(--height);
  position: fixed;
  border: 2px solid var(--color-accB);
  display: none;
  z-index: 10;
`;

const SelectionStartMarkerClass = css`
  top: var(--yPx);
  left: calc(var(--xPx));
  border-top-left-radius: var(--selection-border-radius);
  border-right: 0;
  border-bottom: 0;
`;

const SelectionEndMarkerClass = css`
  top: calc(var(--yPx) + var(--height));
  left: calc(var(--xPx) - var(--width));
  border-bottom-right-radius: var(--selection-border-radius);
  border-top: 0;
  border-left: 0;
`;

