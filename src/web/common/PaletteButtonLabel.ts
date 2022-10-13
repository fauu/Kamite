import { styled } from "solid-styled-components";

import { LAYOUT_BREAKPOINT_SMALL } from "~/style";

export const PaletteButtonLabel = styled.span`
  padding: 0 0.8rem;

  ${LAYOUT_BREAKPOINT_SMALL} {
    padding: 0 0.6rem;
  }
`;
