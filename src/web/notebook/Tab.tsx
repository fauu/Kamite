import { type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { ChromeClassName } from "~/globalStyles";
import type { useGlobalTooltip } from "~/GlobalTooltip";

import { tooltipAnchor } from "~/directives";
const [_] = [tooltipAnchor];

import { lookupTargetSymbolToLookupTabID, type NotebookTab as NotebookTabType } from "./tabs";

// ROBUSTNESS: Could generate at compile time based on the icon directory contents
const LOOKUP_TABS_WITH_ICONS_IDS =
  ["DEP", "ICH", "JDB", "GLI"].map(n => lookupTargetSymbolToLookupTabID(n));

interface NotebookTabProps {
  tab: NotebookTabType,
  active: boolean,
  tooltip: NonNullable<ReturnType<typeof useGlobalTooltip>>,
  onClick: () => void,
}

export const NotebookTab: VoidComponent<NotebookTabProps> = (props) => {
  const hasIcon = !props.tab.lookup || LOOKUP_TABS_WITH_ICONS_IDS.includes(props.tab.id);
  return <div
      role="button"
      class={NotebookTabDisplayClass}
      classList={{
        [ActiveClass]: props.active,
        [HighlightedClass]: props.tab.highlighted,
        [WithIconClass]: hasIcon,
        [RemoteLookupClass]: props.tab.lookup && props.tab.lookup.newTab,
        "notebook-tab": true,
      }}
      style={{ "--icon-url": `url("icons/${props.tab.id}.svg")` }}
      use:tooltipAnchor={{ tooltip: props.tooltip, header: props.tab.title }}
      onClick={props.onClick}
    >
      {props.tab.lookup && !hasIcon && props.tab.lookup.symbol}
    </div>;
};

export const NotebookTabDisplayClass = css`
  background: var(--color-bg2);
  width: var(--notebook-tab-size);
  height: var(--notebook-tab-size);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: -0.02rem;

  box-shadow: inset 0px 0px 1px var(--color-bg3-hl);
  .${ChromeClassName} & {
    box-shadow: inset 0px 0px 2px var(--color-bg3-hl);
  }

  &:hover {
    cursor: pointer;
    box-shadow: inset 0px 0px 5px var(--color-accC);
  }
`;

const ActiveClass = css`
  --active-bg: linear-gradient(45deg, var(--color-bg2) 3%, var(--color-bg3-hl) 53%, var(--color-bg2) 100%);
  background: var(--active-bg);

  &:after {
    content: "";
    width: 100%;
    height: 100%;
  }

  &:not(:hover):after {
    border-bottom: 2px solid var(--color-fg5);
  }
`;

const HighlightedClass = css`
  &:after {
    content: "";
    width: 100%;
    height: 100%;
    background: radial-gradient(circle at -25% -25%, var(--color-accB2) 0%, transparent 45%);
  }
`;


const WithIconClass = css`
  --icon-bg: var(--icon-url) no-repeat center center;
  --icon-bg-size: 34px;
  background: var(--color-bg2) var(--icon-bg);
  background-size: var(--icon-bg-size);

  &.${ActiveClass} {
    background: var(--icon-bg), var(--active-bg);
    background-size: var(--icon-bg-size), cover;
  }
`;

const RemoteLookupClass = css`
  &:before {
    content: "";
    position: absolute;
    top: 4px;
    right: 4px;
    width 8px;
    height: 8px;
    background-image: url("icons/remote.png");
    opacity: 0.5;
  }
`;
