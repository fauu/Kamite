import { createSignal, For, onCleanup, onMount, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import {
  type GeneralScrollPosition, horizontalWheelScroll, onGeneralHorizontalScrollPositionChange
} from "~/directives";
const [_, __] = [horizontalWheelScroll, onGeneralHorizontalScrollPositionChange];

import {
  LAYOUT_BREAKPOINT_SMALL, PaletteButtonClass, PaletteButtonDisabledClass
} from "~/globalStyles";

import type { Action } from ".";

const ACTIONS_WITH_ICONS: Action["kind"][] = ["undo", "redo"];
const HOVER_SCROLL_INTERVAL_MS = 10;
const HOVER_SCROLL_STEP_PX = 20;

interface ActionPaletteProps {
  actions: Action[],
  targetText?: string,
  onAction: (a: Action) => void,
}

export const ActionPalette: VoidComponent<ActionPaletteProps> = (props) => {
  const [buttonsOverflow , setButtonsOverflow] =
    createSignal(false);
  const [buttonsScrollPosition , setButtonsScrollPosition] =
    createSignal<GeneralScrollPosition>("start");

  let buttonsEl!: HTMLDivElement;
  let leftScrollerEl!: HTMLDivElement;
  let rightScrollerEl!: HTMLDivElement;

  const handleButtonClick = (a: Action) => props.onAction(a);

  const observeButtonsOverflow = (): ResizeObserver => {
    const buttonsResizeObserver = new ResizeObserver(() => {
      setButtonsOverflow(buttonsEl.scrollWidth > buttonsEl.clientWidth);
    });
    buttonsResizeObserver.observe(buttonsEl);
    return buttonsResizeObserver;
  };

  const setupScrollers = () => {
    let leftScrollInterval: NodeJS.Timer;
    leftScrollerEl.addEventListener("mouseenter", () => {
      leftScrollInterval = setInterval(() =>
        buttonsEl.scrollBy(-HOVER_SCROLL_STEP_PX, 0), HOVER_SCROLL_INTERVAL_MS
      );
    });
    leftScrollerEl.addEventListener("mouseleave", () => {
      clearInterval(leftScrollInterval);
    });

    let rightScrollInterval: NodeJS.Timer;
    rightScrollerEl.addEventListener("mouseenter", () => {
      rightScrollInterval = setInterval(() =>
        buttonsEl.scrollBy(HOVER_SCROLL_STEP_PX, 0), HOVER_SCROLL_INTERVAL_MS
      );
    });
    rightScrollerEl.addEventListener("mouseleave", () => {
      clearInterval(rightScrollInterval);
    });
  };

  onMount(() => {
    const resizeObserver = observeButtonsOverflow();
    setupScrollers();

    onCleanup(() => resizeObserver.disconnect());
  });

  return <Root id="action-palette">
    <LeftFade
      style={{
        "display": buttonsScrollPosition() !== "start"
          ? "initial"
          : "none"
      }}
    >
      <Scroller ref={leftScrollerEl}/>
    </LeftFade>
    <RightFade
      style={{
        "display": buttonsOverflow() && buttonsScrollPosition() !== "end"
          ? "initial"
          : "none"
      }}
    >
      <Scroller ref={rightScrollerEl}/>
    </RightFade>
    <div
      class={ButtonsClass}
      use:onGeneralHorizontalScrollPositionChange={
        (position: GeneralScrollPosition) => setButtonsScrollPosition(position)
      }
      ref={el => buttonsEl = el}
    >
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
    </div>
  </Root>;
};

export const ActionButtonClass = "action-button";

const Root = styled.div`
  position: relative;
  overflow: hidden;
`;

const Scroller = styled.div`
  width: 2rem;
  height: 100%;
  position: absolute;
  right: 0;
`;

const Fade = styled.div`
  position: absolute;
  width: 3.5rem;
  height: 100%;
  background: url('icons/scroll-arrow.svg'),
    linear-gradient(270deg, var(--color-bg) 60%, rgba(0, 0, 0, 0) 100%);
  background-repeat: no-repeat;
  background-position: right, right;
  background-size: 32px, cover;
`;

const LeftFade = styled(Fade)`
  left: 0;
  z-index: 10;
  transform: scale(-1);
`;

const RightFade = styled(Fade)`
  right: 0;
`;

const ButtonsClass = css`
  display: flex;
  font-size: 0.9rem;
  font-weight: 500;
  letter-spacing: -0.01rem;
  color: var(--color-fg2);
  overflow: hidden;

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
