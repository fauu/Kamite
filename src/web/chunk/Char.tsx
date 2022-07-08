import { type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { CharMaybeNewline } from "~/common";

interface ChunkCharProps {
  value: string,
  idx: number,
  selected: boolean,
  highlighted: boolean,
}

export const ChunkChar: VoidComponent<ChunkCharProps> = (props) =>
  <Root
    class="issue-9"
    classList={{
      [SelectedClass]: props.selected,
      [HighlightedClass]: props.highlighted,
    }}
    data-char-idx={props.idx}
  >
    <CharMaybeNewline value={props.value} newlineAs={<br/>}/>
  </Root>;

const Root = styled.span`
  &:hover {
    border-bottom: 2px dotted var(--color-accC);
  }
`;

const SelectedClass = css`
  border-bottom: 2px solid var(--color-accB) !important;
`;

const HighlightedClass = css`
  background: var(--color-accB2);
`;
