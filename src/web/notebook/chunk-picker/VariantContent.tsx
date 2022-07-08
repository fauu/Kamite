import { Index, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { CharMaybeNewline, Newline } from "~/common";

interface ChunkPickerVariantContentProps {
  value: string,
}

export const ChunkPickerVariantContent
  : VoidComponent<ChunkPickerVariantContentProps> = (props) => {
  return <Root lang="ja">{() => {
    const chars = props.value.replaceAll("@", "").split("");
    const uniqueCharIndices = getUniqueCharIndices(props.value);

    return <Index each={chars}>{(c, i) =>
      <span 
        class={CharClass}
        classList={{ [UniqueCharClass]: uniqueCharIndices.includes(i) }}
      >
        <CharMaybeNewline value={c()} newlineAs={<Newline/>}/>
      </span>
    }</Index>;
  }}</Root>;
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

function getUniqueCharIndices(content: string): number[] {
  const res: number[] = [];
  Array.from(content).forEach((c, i) => c === "@" && res.push(i + res.length));
  return res;
}
