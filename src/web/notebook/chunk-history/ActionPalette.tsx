import { For, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { ChromeClass, PaletteButtonClass } from "~/globalStyles";
import { themeLayoutFlipped } from "~/theme";

import type { ChunkHistoryAction } from "./Action";

interface ChunkHistoryActionPaletteProps {
  actions: ChunkHistoryAction[],
  onAction: (a: ChunkHistoryAction) => void,
}

export const ChunkHistoryActionPalette: VoidComponent<ChunkHistoryActionPaletteProps> = (props) => {
  const handleButtonClick = (a: ChunkHistoryAction) => props.onAction(a);

  return <Root>
    <For each={props.actions}>{a =>
      <div
        role="button"
        classList={{
          [PaletteButtonClass]: true,
          [SelectionActionPaletteButtonClass]: true,
        }}
        onClick={[handleButtonClick, a]}
      >
        {textLabel(a)}
      </div>
    }</For>
  </Root>;
};

const Root = styled.div`
  display: flex;
  font-size: 0.95rem;
  font-weight: 500;
  overflow-x: auto;
  white-space: nowrap;
  width: max-content;
  height: calc(var(--palette-button-min-size) + var(--palette-button-padding-fix));
  position: absolute;
  box-shadow: var(--shadow-panel);
  margin: 0 auto;
  left: 0;
  right: 0;
  /* QUALITY: Shouldn't need notebook tab size to position properly */
  bottom: ${p => !themeLayoutFlipped(p.theme) ? "0" : "var(--notebook-tab-size)"};
`;

const SelectionActionPaletteButtonClass = css`
  background-color: var(--color-bg4);

  box-shadow: inset 0px 0px 1px var(--color-med2);
  .${ChromeClass} & {
    box-shadow: inset 0px 0px 2px var(--color-med2);
  }

  &:hover {
    background-color: var(--color-med);
  }
`;

function textLabel(action: ChunkHistoryAction): string {
  switch (action) {
    case "copy":
      return "Copy";
    case "copy-original":
      return "Copy original";
    case "reset-selection":
      return "Reset selection";
  }
}
