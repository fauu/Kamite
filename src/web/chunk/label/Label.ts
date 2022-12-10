import { Accessor, createEffect, untrack } from "solid-js";
import { css } from "solid-styled-components";

import { ChunkCharIdxAttrName, ChunkLabelId } from "~/dom";
import { BgFlashingClass, ChromeClass } from "~/style";

import { ChunkTextClass, type Chunk, type ChunkFlashState, type ChunkTextSelection } from "..";

import { CharHeightProp, RubyTextScaleProp } from "./props";

import { LabelSelectionMarkers } from "./SelectionMarkers";

const DEFAULT_RUBY_TEXT_SCALE = 1;

export class ChunkLabel {
  #concealRubies: Accessor<boolean>;
  #movingMouseWhilePrimaryDown: Accessor<boolean>;

  #rootEl: HTMLDivElement;
  #subRootEl: HTMLSpanElement;
  #rubyEls: HTMLElement[] = [];
  #selectionMarkers: LabelSelectionMarkers;

  constructor(
    rootEl: HTMLDivElement,
    concealRubies: Accessor<boolean>,
    movingMouseWhilePrimaryDown: Accessor<boolean>
  ) {
    this.#rootEl = rootEl;
    this.#concealRubies = concealRubies;
    this.#movingMouseWhilePrimaryDown = movingMouseWhilePrimaryDown;

    rootEl.classList.add(RootClass, ChunkTextClass);
    this.#subRootEl = document.createElement("span");
    this.#subRootEl.id = ChunkLabelId;
    this.#rootEl.prepend(this.#subRootEl);

    createEffect(() => {
      this.#toggleRubyConcealing(concealRubies());
    })

    this.#selectionMarkers = new LabelSelectionMarkers(this.#rootEl);
  }

  setChunk(chunk: Chunk) {
    const newLabelChildren: HTMLElement[] = [];
    this.#rubyEls = [];
    let charIdx = 0;
    if (chunk.text.hasFurigana) {
      for (const maybeRuby of chunk.text.maybeRubies!) {
        if (!maybeRuby.text) {
          for (const ch of maybeRuby.base) {
            newLabelChildren.push(this.#makeCharElement(ch, charIdx++));
          }
        } else {
          const rubyEl = document.createElement("ruby");
          const rubyElChildren: HTMLElement[] = [];
          for (const ch of maybeRuby.base) {
            rubyElChildren.push(this.#makeCharElement(ch, charIdx++));
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
            rtEl.style.setProperty(RubyTextScaleProp, rubyTextScale.toString());
          }

          newLabelChildren.push(rubyEl);
        }
      }
    } else {
      for (const ch of chunk.text.base) {
        newLabelChildren.push(this.#makeCharElement(ch, charIdx++));
      }
    }
    this.#subRootEl.replaceChildren(...newLabelChildren);

    // Get character element height for positioning and scaling the selection markers
    if (
      newLabelChildren.length > 0
      && this.#rootEl.style.getPropertyValue(CharHeightProp) === ""
    ) {
      // ASSUMPTION: <ruby> height is equivalent to char's <span> height
      this.#rootEl.style.setProperty(
        CharHeightProp,
        newLabelChildren[0].getBoundingClientRect().height.toString()
      );
    }

    this.#toggleRubyConcealing(untrack(this.#concealRubies));
  }

  #boundHandleRubyMouseOver = ((event: MouseEvent) =>
      this.#handleRubyHoverStateChange(event, true)
    ).bind(this);

  #boundHandleRubyMouseOut = ((event: MouseEvent) =>
      this.#handleRubyHoverStateChange(event, false)
    ).bind(this);

  #boundHandleRubyMouseUp = ((event: MouseEvent) =>
      this.#toggleRubyConcealedClassUntil(event.currentTarget as HTMLElement, false)
    ).bind(this);

  #handleRubyHoverStateChange(event: MouseEvent, hover: boolean) {
    // Don't unconceal while user is selecting inside chunk text to prevent unnecessary flickering
    if (hover && this.#movingMouseWhilePrimaryDown()) {
      return;
    }
    this.#toggleRubyConcealedClassUntil(event.currentTarget as HTMLElement, !hover);
  }

  #toggleRubyConcealing(on: boolean) {
    for (const el of this.#rubyEls) {
      const op = on ? "addEventListener" : "removeEventListener";
      el[op]("mouseover", this.#boundHandleRubyMouseOver as EventListener);
      el[op]("mouseout",  this.#boundHandleRubyMouseOut as EventListener);
      el[op]("mouseup",   this.#boundHandleRubyMouseUp as EventListener);
    }

    this.#toggleRubyConcealedClassUntil("last", on);
  }

  #toggleRubyConcealedClassUntil(targetRubyEl: HTMLElement | "last", on: boolean) {
    for (const el of this.#rubyEls) {
      el.classList.toggle(RubyTextConcealedClass, on);
      if (targetRubyEl !== "last" && el === targetRubyEl) {
        break;
      }
    }
  }

  setSelection(selection?: ChunkTextSelection) {
    this.#rootEl.classList.toggle(HasSelectionClass, selection !== undefined);
    this.#setCharElementsClassByRange(
      [SelectedCharClass, SelectionStartCharClass, SelectionEndCharClass],
      selection?.range
    );
    this.#updateSelectionMarkers(selection);
  }

  #updateSelectionMarkers(selection?: ChunkTextSelection) {
    if (!selection) {
      this.#selectionMarkers.hide();
      return;
    }

    const [start, end] = selection.range;
    const startStr = start.toString();
    const endStr = end.toString();
    let markedStart = false;
    let rubyCorrection: { x: number, y: number } | undefined = undefined;
    this.#forCharElement((charEl, rawCharIdx) => {
      if (!markedStart && rawCharIdx === startStr) {
        // <ruby> elements must have `position: relative` for the purposes of positioning the ruby
        // conceal pseudoelement. This unfortunately means that character elements will have an
        // inconsistent offset parent: the offsets of the characters within <ruby> will be relative
        // to the ruby element, whereas the offsets of the characters outside <ruby> will be
        // relative to the `#rootEl`. This is why, in the case of a character within <ruby>, to
        // calculate its offset relative to the `#rootEl`, we must not just take its own offset
        // values, but also add to them the offsets of the parent <ruby>.
        rubyCorrection =
          charEl.offsetParent !== this.#rootEl
          ? this.#offsetRubyCorrection(charEl)
          : { x: 0, y: 0 }
        this.#selectionMarkers.showLeftMarkerAt(
          charEl.offsetLeft + rubyCorrection.x,
          charEl.offsetTop + rubyCorrection.y,
        );
        markedStart = true;
      } else {
        rubyCorrection = undefined;
      }
      if (markedStart && rawCharIdx === endStr) {
        if (!rubyCorrection) {
          rubyCorrection =
            charEl.offsetParent !== this.#rootEl
            ? this.#offsetRubyCorrection(charEl)
            : { x: 0, y: 0 }
        }
        this.#selectionMarkers.showRightMarkerAt(
          charEl.offsetLeft + charEl.offsetWidth + rubyCorrection.x,
          charEl.offsetTop + rubyCorrection.y,
        );
        return false; // Stop iterating
      }
    });
  }

  #offsetRubyCorrection(charEl: HTMLElement): { x: number, y: number } {
    return { x: charEl.parentElement!.offsetLeft, y: charEl.parentElement!.offsetTop };
  }

  setHighlight(highlight: [number, number] | undefined) {
    this.#setCharElementsClassByRange(
      [HighlightedCharClass, RangeWithBgFirstCharClass, RangeWithBgLastCharClass],
      highlight
    );
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
    this.#setCharElementsClassByRange(
      [BgFlashingClass, RangeWithBgFirstCharClass, RangeWithBgLastCharClass],
      newFlashingRange
    );
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
    [everyClass, firstClass, lastClass]: [string, string, string],
    range: [number, number] | undefined
  ) {
    let everyClassOp: "add" | "remove" | undefined;
    let idx: number | undefined = undefined;
    if (!range) {
      everyClassOp = "remove";
    } else {
      idx = parseInt(rawCharIdx);
      const inRange = idx >= range[0] && idx <= range[1];
      everyClassOp = inRange ? "add" : "remove";
    }
    const rangeDefined = !(range === undefined);
    charEl.classList.toggle(firstClass, rangeDefined && idx === range[0]);
    charEl.classList.toggle(lastClass,  rangeDefined && idx === range[1]);
    charEl.classList[everyClassOp](everyClass);
  }

  #setCharElementsClassByRange(
    classNames: [string, string, string],
    range: [number, number] | undefined
  ) {
    this.#forCharElement((charEl, rawCharIdx) =>
      this.#setCharElementClassByRange(charEl, rawCharIdx, classNames, range)
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
  ${RubyTextScaleProp}: ${DEFAULT_RUBY_TEXT_SCALE.toString()};
  --ruby-text-font-size-base: calc(0.5 * var(--chunk-font-size) * var(--chunk-furigana-font-scale));
  --selection-border-radius: 2px;

  position: relative;
  box-sizing: content-box;
  margin-top: var(--text-margin-top);

  /* Sub-root */
  #${ChunkLabelId} {
    border-radius: var(--border-radius-default);
  }

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
      font-size: calc(1em * var(${RubyTextScaleProp}));
      font-weight: var(--chunk-furigana-font-weight);
    }
  }

  ::selection {
    background: transparent;
  }
`;

const RubyTextConcealedClass = css`
  position: relative;

  /* Concealing rectangle */
  &:before {
    content: "";
    position: absolute;
    top: calc(-1 * var(--ruby-text-font-size-base) - 2px);
    left: calc(0rem - 0.05rem);
    width: calc(100% + 0.12rem /* sic */);
    height: calc(var(--ruby-text-font-size-base) + 0.3rem);
    background: var(--color-bg2-hl);
    filter: blur(1px);
  }
`;

const CharClass = css`
  position: relative;

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

const RangeWithBgFirstCharClass = css`
  border-top-left-radius: var(--border-radius-default);
  border-bottom-left-radius: var(--border-radius-default);
`;

const RangeWithBgLastCharClass = css`
  border-top-right-radius: var(--border-radius-default);
  border-bottom-right-radius: var(--border-radius-default);
`;

const SelectedCharClass = css`
  background: linear-gradient(0, var(--color-bg3) 0%, transparent 100%);
  border-bottom: 1px solid var(--color-bg3-hl);
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
  background: linear-gradient(0, var(--color-accB2) 0%, transparent 100%) !important;
  border-bottom: 1px solid var(--color-accB2-hl2);
  z-index: 5;
`;
