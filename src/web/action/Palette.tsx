import { For, type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { horizontalWheelScroll } from "~/directives";
const [_] = [horizontalWheelScroll];

import {
  LAYOUT_BREAKPOINT_SMALL, PaletteButtonClass, PaletteButtonDisabledClass
} from "~/globalStyles";

import type { Action } from ".";

const ACTIONS_WITH_ICONS: Action["kind"][] = ["undo", "redo"];

interface ActionPaletteProps {
  actions: Action[],
  targetText?: string,
  onAction: (a: Action) => void,
}

export const ActionPalette: VoidComponent<ActionPaletteProps> = (props) => {
  const handleButtonClick = (a: Action) => props.onAction(a);

  return <div class={RootClass} use:horizontalWheelScroll id="action-palette">
    <For each={props.actions}>{(a) => {
      const hasIcon = ACTIONS_WITH_ICONS.includes(a.kind);
      return <div
        role="button"
        class={ButtonClass}
        classList={{
          [PaletteButtonClass]: true,
          [PaletteButtonDisabledClass]: a.disabled,
          [ActionButtonClass]: true,
        }}
        style={{ "background-image": hasIcon && `url('icons/${a.kind}.svg')` }}
        innerHTML={!hasIcon ? textLabel(a, props.targetText) : undefined}
        onClick={[handleButtonClick, a]}
      />;
    }}</For>
  </div>;
};

export const ActionButtonClass = "action-button";

const RootClass = css`
  display: flex;
  font-size: 0.9rem;
  font-weight: 500;
  letter-spacing: -0.01rem;
  color: var(--color-fg2);
  white-space: nowrap;

  /* Both needed for Firefox+Chrome */
  overflow: auto;
  overflow-x: overlay;

  ${LAYOUT_BREAKPOINT_SMALL} {
    letter-spacing: -0.01rem;
    font-size: 0.8rem;
  }
`;

const ButtonClass = css`
  width: max-content;
  flex-shrink: 0;

  sup {
    line-height: 100%;
  }

  ${LAYOUT_BREAKPOINT_SMALL} {
    padding: 0 0.6rem;
  }

  &${PaletteButtonDisabledClass} {
    opacity: 0.5;
    cursor: default;
    pointer-events: none;
  }
`;

function textLabel(action: Action, targetText?: string): string {
  switch (action.kind) {
    case "select-all":
      return "Select all";
    case "select-highlighted":
      return "Select highlighted";
    case "delete-selected":
      return "Delete";
    case "duplicate-selected":
      return "Duplicate";
    case "delete-every-second-char":
      return "Delete every 2<sup>nd</sup> char.";
    case "copy-all":
      return "Copy all";
    case "copy-selected":
      return "Copy";
    case "copy-original":
      return "Copy original";
    case "transform-selected":
      return `${targetText!} ➞ ${action.into}`;
    case "hiragana-to-katakana":
      return "To katakana";
    case "katakana-to-hiragana":
      return "To hiragana";
  }
  return "";
}
