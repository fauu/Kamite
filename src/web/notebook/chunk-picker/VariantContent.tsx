import { Index, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import type { ChunkVariant } from "~/backend";
import { CharMaybeNewline, Newline } from "~/common";

interface ChunkPickerVariantContentProps {
  variant: ChunkVariant,
}

export const ChunkPickerVariantContent : VoidComponent<ChunkPickerVariantContentProps> = props => {
  const uniqueCharIndices = props.variant.enhancements.interVariantUniqueCharacterIndices || [];

  return <Root lang="ja">
    <Index each={props.variant.content.split("")}>{(c, i) =>
      <span
        class={CharClass}
        classList={{ [UniqueCharClass]: uniqueCharIndices.includes(i) }}
      >
        <CharMaybeNewline value={c()} newlineAs={<Newline/>}/>
      </span>
    }</Index>;
  </Root>;
};

const Root = styled.span`
  font-size: 1.8em;
  user-select: text;
`;

const CharClass = css`
  user-select: text;
`;

const UniqueCharClass = css`
  color: var(--color-accB);
`;
