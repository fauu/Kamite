import { Accessor, type JSX, type ParentComponent } from "solid-js";
import { css } from "solid-styled-components";

import { ChromeClass } from "~/style";

import { holdClickEvent, tooltipAnchor } from "~/directives";
const [_, __] = [holdClickEvent, tooltipAnchor];

import { useGlobalTooltip } from "~/GlobalTooltip";

interface StatusPanelIndicatorProps {
  tooltipHeader: string,
  tooltipBody: JSX.Element,
  forceHideTooltip?: Accessor<boolean>,
  onHoldClick?: () => void,
  onClick?: () => void,
  id: string,
}

export const StatusPanelIndicator: ParentComponent<StatusPanelIndicatorProps> = (props) => {
  const tooltip = useGlobalTooltip()!;

  return <div class={ContainerClass} id={props.id}>
    <div
      class={RootClass}
      use:holdClickEvent={{
        durationMS: 800,
        holdClickCb: props.onHoldClick,
        regularClickCb: props.onClick,
      }}
      use:tooltipAnchor={{
        tooltip,
        header: props.tooltipHeader,
        body: props.tooltipBody,
        forceHide: props.forceHideTooltip
      }}
    >
      {props.children}
    </div>
  </div>;
};

const ContainerClass = css`
  &:not(:last-child) {
    margin-right: 0.5rem;
  }
`;

const RootClass = css`
  padding: 0.4rem 0.5rem 0.25rem 0.5rem;
  font-weight: 500;
  background: var(--color-bg);
  color: var(--color-fg3);
  border: 1px solid var(--color-bg2);
  border-radius: var(--border-radius-default);
  width: max-content;

  box-shadow: inset 0px 0px 1px var(--color-bgm1);
  .${ChromeClass} & {
    box-shadow: inset 0px 0px 2px var(--color-bgm1);
  }

  &:hover {
    background: linear-gradient(0, var(--color-bg-hl) 0%, transparent 100%);
  }
`;
