import { type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

interface ChunkPickerVariantDebugProps {
  labels: string[],
  score: number,
}

export const ChunkPickerVariantDebug
  : VoidComponent<ChunkPickerVariantDebugProps> = (props) => {
  return <Root>
    <Label>{labelsToString(props.labels)}</Label>
    <Score>{props.score}</Score>
  </Root>;
};

const Root = styled.div`
  position: absolute;
  top: -10px;
  right: 0;
  font-size: 0.8rem;
  margin-left: 1rem;
  padding: 0.08rem 0.25rem;
  background-color: var(--color-fg3);
  color: var(--color-bg);
  opacity: 0.25;
  border-radius: var(--border-radius-default);

  &:hover {
    opacity: 1;
  }
`;

const Label = styled.span`
  margin-right: 0.25rem;
  font-weight: 500;
`;

const Score = styled.span`
  color: var(--color-bg4);
`;

function labelsToString(labels: string[]): string {
  let res = labels[0];
  if (labels.length > 1) {
    res += `, ${labels[1]}`;
  }
  if (labels.length > 2) {
    res += ` + ${labels.length - 2} more`;
  }
  return res;
}

