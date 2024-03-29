import { Show, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { DefaultIcon } from "~/common";
import type { useGlobalTooltip } from "~/GlobalTooltip";
import { ChromeClass } from "~/style";

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

export const NotebookTab: VoidComponent<NotebookTabProps> = props => {
  const hasIcon = !props.tab.lookup || LOOKUP_TABS_WITH_ICONS_IDS.includes(props.tab.id);
  return <div
      role="button"
      class={NotebookTabDisplayClass}
      classList={{
        [NotebookTabActiveClass]: props.active,
        [NotebookTabHighlightedClass]: props.tab.highlighted,
        "notebook-tab": true,
      }}
      use:tooltipAnchor={{ tooltip: props.tooltip, header: props.tab.title }}
      onClick={props.onClick}
    >
      <Show when={hasIcon}>
        <Icon iconName={props.tab.id} sizePx={34} />
      </Show>
      <Show when={props.tab.lookup && props.tab.lookup.newTab}>
        <RemoteLookupIndicator />
      </Show>
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
  .${ChromeClass} & {
    box-shadow: inset 0px 0px 2px var(--color-bg3-hl);
  }

  &:hover {
    cursor: pointer;
    box-shadow: inset 0px 0px 5px var(--color-accC);
  }
`;

const Icon = styled(DefaultIcon)`
  position: absolute;
  width: 100%;
`;

const RemoteLookupIndicator: VoidComponent = () =>
  <RemoteLookupIndicatorRoot iconName="remote" sizePx={8} />;

const RemoteLookupIndicatorRoot = styled(DefaultIcon)`
  background: var(--color-med2);
  height: ${p => p.sizePx}px;
  position: absolute;
  top: 4px;
  right: 4px;
`;

const NotebookTabActiveClass = css`
  background: linear-gradient(
    45deg,
    var(--color-bg2) 3%,
    var(--color-bg3-hl) 53%,
    var(--color-bg2) 100%
  );

  &:after {
    content: "";
    width: 100%;
    height: 100%;
  }

  &:not(:hover):after {
    border-bottom: 2px solid var(--color-fg5);
  }
`;

const NotebookTabHighlightedClass = css`
  &:after {
    position: absolute;
    content: "";
    width: 100%;
    height: 100%;
    background: radial-gradient(circle at -25% -25%, var(--color-accB2) 0%, transparent 45%);
  }
`;
