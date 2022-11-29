import { type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { ConfigKey } from "~/common";
import { themeLayoutFlipped } from "~/theme";

import type { NotebookHeight } from "./Height";
import { MAX_HEIGHT_PERCENT, MIN_HEIGHT_PERCENT } from "./State";

interface NotebookHeightHudProps {
  configKey: string,
  height: NotebookHeight,
}

export const NotebookHeightHud: VoidComponent<NotebookHeightHudProps> = (props) => {
  const heightConstsPercentDisplay = {
    min: Math.round(MIN_HEIGHT_PERCENT * 100),
    max: Math.round(MAX_HEIGHT_PERCENT * 100),
  };

  return <Root id="notebook-height-hud">
    <ConfigKey value={props.configKey}/>
    <Height>
      {clamp(
        heightPercentDisplay(props.height.percent),
        heightConstsPercentDisplay.min,
        heightConstsPercentDisplay.max
      )}
    </Height>
  </Root>;
};

const Root = styled.div`
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  background: var(--color-bg);
  border: 1px dashed var(--color-bg3);
  border-radius: var(--border-radius-default);
  padding: 0.15rem 0.28rem;
  ${p => !themeLayoutFlipped(p.theme) ? "top" : "bottom"}: -50px;
  z-index: 5;
`;

const Height = styled.span`
  color: var(--color-fg3);
  font-weight: 500;
  font-size: 0.9rem;
  margin-left: 0.3rem;
`;

function clamp(n: number, min: number, max: number) {
  return Math.min(Math.max(n, min), max);
}

function heightPercentDisplay(heightPercent: number) {
  return Math.round(heightPercent * 100);
}
