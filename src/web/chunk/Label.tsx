import { For, Show, type Ref, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { range, YomichanSentenceDelimiter } from "~/common";
import { BgFlashingClass } from "~/globalStyles";

import { ChunkChar } from "./Char";
import { ChunkCharString } from "./CharString";
import type { ChunkText } from "./Text";
import { ChunkTextClass } from "./TextClass";
import type { ChunkTextSelection } from "./TextSelectionState";

interface ChunkLabelProps {
  text: ChunkText,
  flashing: boolean,
  selection?: ChunkTextSelection,

  // We need to display the highlight, i.e. the browser native selection, manually, because Chrome
  // apparently doesn't realize that "user-select: none" doesn't mean that a selection can't be
  // created programatically, and consequently fails to display it at all in such case
  highlight?: [number, number],

  ref: Ref<HTMLSpanElement>,
}

export const ChunkLabel: VoidComponent<ChunkLabelProps> = (props) =>
  <Root lang="ja" class={ChunkTextClass}>
    <span
      classList={{ [BgFlashingClass]: props.flashing }}
      ref={props.ref}
      id="chunk-label"
    >
      <Show
        when={props.text.hasFurigana}
        fallback={
          <ChunkCharString
            value={props.text.base}
            selected={
              range(props.text.base.length)
                .map(i => isCharSelected(props.selection, i))
            }
            highlighted={
              range(props.text.base.length)
                .map(i => isCharHighlighted(props.highlight, i))
            }
          />
        }
      >
        <For each={props.text.maybeRubiesWithOffset}>{mro =>
          <Show
            when={mro.maybeRuby.text}
            fallback={
              <ChunkChar
                value={mro.maybeRuby.base}
                selected={isCharSelected(props.selection, mro.offset)}
                highlighted={isCharHighlighted(props.highlight, mro.offset)}
                idx={mro.offset}
              />
            }
          >
            <ruby>
              <ChunkCharString
                value={mro.maybeRuby.base}
                selected={
                  range(mro.maybeRuby.base.length)
                    .map(i => isCharSelected(props.selection, mro.offset + i))
                }
                highlighted={
                  range(props.text.base.length)
                    .map(i => isCharHighlighted(props.highlight, mro.offset + i))
                }
                startIdx={mro.offset}
              />
              <rt>{mro.maybeRuby.text}</rt>
            </ruby>
          </Show>
        }</For>
      </Show>
    </span>
    <YomichanSentenceDelimiter/>
  </Root>;

const Root = styled.div`
  box-sizing: content-box;
  margin-top: 0.3rem;

  rt {
    font-size: 1.18rem;
    margin-bottom: -0.1em;
  }

  ::selection {
    background: transparent;
  }
`;

function isCharSelected(selection: ChunkTextSelection | undefined, idx: number): boolean {
  return (selection?.range && rangeIncludes(selection.range, idx)) ?? false;
}

function isCharHighlighted(range: [number, number] | undefined, idx: number): boolean {
  return (range && rangeIncludes(range, idx)) ?? false;
}

function rangeIncludes([a, b]: [number, number], x: number): boolean {
  return x >= a && x <= b;
}
