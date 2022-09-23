import { For, Show, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { scrollToBottomOnChildListChange, tooltipAnchor } from "~/directives";
const [_, __] = [scrollToBottomOnChildListChange, tooltipAnchor];

import type { ChunksState } from "~/chunk";
import { Newline, StringWithNewlines } from "~/common";
import { BgFlashingClass, ChromeClass } from "~/style";
import { useGlobalTooltip } from "~/GlobalTooltip";

import type { ChunkHistoryAction } from "./Action";
import { ChunkHistoryActionPalette } from "./ActionPalette";
import { Checkbox } from "./Checkbox";

interface ChunkHistoryProps {
  state: ChunksState,
  availableSelectionActions: ChunkHistoryAction[],
  onEntryClick: (idx: number) => void,
  onAction: (action: ChunkHistoryAction) => void,
}

export const ChunkHistory: VoidComponent<ChunkHistoryProps> = (props) => {
  const tooltip = useGlobalTooltip()!;

  const handleEntrySelectCheckboxChange = (idx: number, event: Event) =>
    props.state.select(idx, (event.target as HTMLInputElement).checked);

  return <div
      class={RootClass}
      use:scrollToBottomOnChildListChange={{ scrollParent: true }}
      id="chunk-history"
    >
      <For each={props.state.chunks}>{(c, i) =>
        <Entry
          lang="ja"
          class="issue-9"
          classList={{
            [PointedToClass]: i() === props.state.pointer(),
            [Future]: i() > props.state.pointer(),
            [BgFlashingClass]: c.historyFlashing,
          }}
        >
          <Show when={c.text.length !== 0}>
            <CheckboxContainer>
              <Checkbox
                checked={c.selected}
                onChange={(e) => handleEntrySelectCheckboxChange(i(), e)}
              />
            </CheckboxContainer>
          </Show>
          <Show
            when={c.text.length !== 0}
            fallback={<Empty/>}
          >
            <span
              class={EntryTextClass}
              onClick={[props.onEntryClick, i()]}
              use:tooltipAnchor={{
                tooltip,
                body: <StringWithNewlines value={c.text.base} newlineAs={<br/>}/>
              }}
            >
              <StringWithNewlines value={c.text.base} newlineAs={<Newline/>}/>
            </span>
          </Show>
          <Show when={c.translation}>
            <span
              class={TranslationLabelClass}
              use:tooltipAnchor={{
                tooltip,
                body: c.translation?.text,
                delayMS: 0,
              }}
            >
              Translation
            </span>
          </Show>
        </Entry>
      }</For>
      <ChunkHistoryActionPalette
        actions={props.availableSelectionActions}
        onAction={props.onAction}
      />
    </div>;
};

const RootClass = css`
  --translation-label-height: 25px;
  --padding: 5px;
  --entry-height: calc(var(--translation-label-height) + 2 * var(--padding));
`;

const Entry = styled.div`
  border-radius: var(--border-radius-default);
  height: var(--entry-height);
  display: flex;
  align-items: center;
  padding: 0 var(--padding);

  &:not(:last-child) {
    margin-bottom: 0.45rem;
  }
`;

const CheckboxContainer = styled.div`
  margin: 0 0.5rem 0 0.2rem;
`;

const Empty = styled.span`
  padding-left: 2px;
  font-size: 1.1rem;
  color: var(--color-med2);
  &:after {
    content: "[empty]";
  }
`;

const PointedToClass = css`
  outline: 1px solid var(--color-bg4);

  box-shadow: inset 0px 0px 1px var(--color-bgm1);
  .${ChromeClass} & {
    box-shadow: inset 0px 0px 2px var(--color-bgm1);
  }
`;

const Future = css`
  color: var(--color-med);
`;

const EntryTextClass = css`
  cursor: pointer;
  font-size: 1.35rem;
  height: var(--entry-height);
  line-height: calc(var(--translation-label-height) - 2px);
  padding-top: calc(var(--padding) + 1px);
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;

  &:hover {
    color: var(--color-accA);
  }
`;

const TranslationLabelClass = css`
  background: var(--color-bg2);
  border-radius: var(--border-radius-default);
  margin-left: 0.8rem;
  font-size: 0.75rem;
  padding: 0 0.4rem;
  height: var(--translation-label-height);
  line-height: var(--translation-label-height);
  cursor: default;
  align-self: center;
  box-shadow: inset 0px 0px 1px var(--color-bg3);
`;
