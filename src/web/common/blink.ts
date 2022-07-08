import { css, keyframes } from "solid-styled-components";

const blinkKeyframes = keyframes`
  0% {
    visibility: hidden;
  }
  50% {
    visibility: visible;
  }
`;

const halfblinkKeyframes = keyframes`
  0% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
`;

export const BlinkingClass = css`
  animation: 2s step-end infinite ${blinkKeyframes};
`;

export const HalfBlinkingClass = css`
  animation: 4s step-end infinite ${halfblinkKeyframes};
`;
