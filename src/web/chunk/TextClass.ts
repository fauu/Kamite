import { css } from "solid-styled-components";

export const ChunkTextClass = css`
  --line-height: calc(var(--chunk-line-height-scale) * var(--chunk-font-size) * 1.35);
  --text-margin-top: 0.3rem;

  font-size: var(--chunk-font-size);
  font-weight: var(--chunk-font-weight);
  letter-spacing: var(--chunk-letter-spacing);
  line-height: var(--line-height);
  padding: 0 0.3rem 0.2rem 0.3rem;
`;
