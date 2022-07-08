import { styled } from "solid-styled-components";

export const Newline = styled.span`
  color: var(--color-med2);
  &:after {
    content: "↵";
  }
`;
