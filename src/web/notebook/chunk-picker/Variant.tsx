import { Show, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import type { ChunkVariant } from "~/backend";

import { ChunkPickerVariantContent } from "./VariantContent";
import { ChunkPickerVariantDebug } from "./VariantDebug";
import {
  ChunkPickerVariantPickButton, ChunkPickerVariantPickButtonClass
} from "./VariantPickButton";

interface ChunkPickerVariantProps {
  variant: ChunkVariant,
  isBeingSelectedIn: boolean,
  idx: number,
  debug: boolean,
  onPick: () => void,
}

export const ChunkPickerVariant: VoidComponent<ChunkPickerVariantProps> = (props) =>
  <Root data-variant-idx={props.idx}>
    <ChunkPickerVariantContent value={props.variant.content}/>
    <Show when={props.debug}>
      <ChunkPickerVariantDebug labels={props.variant.labels} score={props.variant.score}/>
    </Show>
    <ChunkPickerVariantPickButton
      variantBeingSelectedIn={props.isBeingSelectedIn}
      onClick={props.onPick}
    />
  </Root>;

const Root = styled.div`
  position: relative;
  margin-bottom: 0.8rem;
  padding-bottom: 0.9rem;

  &:not(:last-child) {
    border-bottom: 1px solid var(--color-bg3);
  }

  ${ChunkPickerVariantPickButtonClass} {
    opacity: 0.1;
  }

  &:hover {
    ${ChunkPickerVariantPickButtonClass} {
      opacity: 0.9;
    }
  }
`;
