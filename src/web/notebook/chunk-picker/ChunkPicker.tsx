import {
  batch, createSelector, createSignal, For, onCleanup, Show, type Ref, type VoidComponent
} from "solid-js";
import { styled } from "solid-styled-components";

import type { ChunkVariant } from "~/backend";

import { ChunkPickerVariant } from "./Variant";

interface ChunkPickerProps {
  variants: ChunkVariant[],
  debug: boolean,
  onPick: (text: string) => void,
  ref: Ref<HTMLDivElement>,
}

export const ChunkPicker: VoidComponent<ChunkPickerProps> = (props) => {
  const [variantBeingSelectedInIdx, setVariantBeingSelectedInIdx] =
    createSignal<number>();
  const isVariantBeingSelectedIn =
    createSelector(variantBeingSelectedInIdx);
  const [selectionText, setSelectionText] =
    createSignal<string>();

  const handlePick = (variantIdx: number) => {
    const toPick =
      variantBeingSelectedInIdx() === variantIdx && selectionText()
      ? selectionText()!
      : props.variants[variantIdx].content;
    props.onPick(toPick);
    selectionText() && document.getSelection()?.removeAllRanges();
  };

  const handleSelectionChange = () => {
    const sel = document.getSelection();
    const anchorNode = sel?.anchorNode;
    const anchorParentEl = anchorNode?.parentElement;

    const variantEl = anchorParentEl?.parentElement?.parentElement;
    const variantIdx = variantEl?.dataset["variantIdx"];

    let variantBeingSelectedInIdx: number | undefined;
    let selectionText: string | undefined;
    const selStr = sel?.toString();
    if (variantIdx && sel && selStr !== "") {
      variantBeingSelectedInIdx = parseInt(variantIdx);
      selectionText = selStr;
    }
    batch(() => {
      setVariantBeingSelectedInIdx(variantBeingSelectedInIdx);
      setSelectionText(selectionText);
    });
  };

  document.addEventListener("selectionchange", handleSelectionChange);
  onCleanup(() => document.removeEventListener("selectionchange", handleSelectionChange));

  return <Root ref={props.ref} id="chunk-picker">
    <Show
      when={props.variants.length > 0}
      fallback={
        <EmptyMsg>
          (If text recognizer gives alternative results, they will appear here.)
        </EmptyMsg>
      }
    >
      <For each={props.variants}>{(v, i) =>
        <ChunkPickerVariant
          variant={v}
          isBeingSelectedIn={isVariantBeingSelectedIn(i())}
          idx={i()}
          debug={props.debug}
          onPick={() => handlePick(i())}
        />
      }</For>
    </Show>
  </Root>;
};

const Root = styled.div`
  line-height: 1.25;
  display: flex;
  flex-direction: column;
  flex: 1;
`;

const EmptyMsg = styled.span`
  color: var(--color-fg4);
  font-size: 1.1rem;
`;
