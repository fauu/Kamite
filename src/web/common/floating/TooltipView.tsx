import { type ParentComponent, Show } from "solid-js";
import { Portal } from "solid-js/web";
import { styled } from "solid-styled-components";

import type { Tooltip } from "~/common/floating";

interface TooltipViewProps {
  state: Tooltip,
  header?: string,
}

export const TooltipView: ParentComponent<TooltipViewProps> = (props) =>
  <Portal>
    <Root
      role="tooltip"
      style={{
        visibility: props.state.visible() ? "visible" : "hidden",
        left: `${props.state.floating.x ?? 0}px`,
        top: `${props.state.floating.y ?? 0}px`,
        position: props.state.floating.strategy,
        "font-size": `${props.state.scale() * FONT_SIZE_DEFAULT_REM}rem`,
        "padding": `${props.state.scale() * PADDING_VERTICAL_DEFAULT_PX}px ${props.state.scale() * PADDING_HORIZONTAL_DEFAULT_PX}px`,
      }}
      ref={props.state.floating.setFloating}
      id="tooltip"
    >
      <Header>
        <Show when={props.header} fallback={props.state.header()}>
          {props.header}
        </Show>
      </Header>
      <Body
        classList={{
          [SecondaryClass]: props.header !== undefined || props.state.header() !== undefined 
        }}
      >
        <Show when={props.children} fallback={props.state.body()}>
          {props.children}
        </Show>
      </Body>
    </Root>
  </Portal>;

const FONT_SIZE_DEFAULT_REM = 0.9;
const PADDING_VERTICAL_DEFAULT_PX = 3;
const PADDING_HORIZONTAL_DEFAULT_PX = 7;

const Root = styled.div`
  color: var(--color-fg);
  background: var(--color-bgm1);
  border: 1px solid var(--color-bgm2);
  border-radius: var(--border-radius-default);
  box-shadow: var(--shadow-panel);
  pointer-events: none;
  z-index: 100;
`;

const Header = styled.div`
  font-weight: 500;
`;

const SecondaryClass = "secondary";

const Body = styled.div`
  &.${SecondaryClass} {
    color: var(--color-fg4);
  }
`;
