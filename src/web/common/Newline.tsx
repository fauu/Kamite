import type { VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

export const Newline: VoidComponent = () => <Root>{"↵"}</Root>;

const Root = styled.span`
  color: var(--color-med2);
`;
