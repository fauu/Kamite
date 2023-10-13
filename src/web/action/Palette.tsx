import {
  createSignal, For, onCleanup, onMount, Show, type Ref, type VoidComponent
} from "solid-js";
import { css, styled } from "solid-styled-components";

import {
  holdClickEvent, horizontalWheelScroll, onGeneralHorizontalScrollPositionChange, tooltipAnchor,
  type GeneralScrollPosition
} from "~/directives";
const [_, __, ___, ____] = [
  horizontalWheelScroll, onGeneralHorizontalScrollPositionChange, tooltipAnchor, holdClickEvent
];

import { DefaultIcon, PaletteButtonLabel } from "~/common";
import { useGlobalTooltip } from "~/GlobalTooltip";
import {
  LAYOUT_BREAKPOINT_SMALL, PaletteButtonClass, PaletteButtonDisabledClass
} from "~/style";

import { actionKinds, type Action, type ActionInvocation } from ".";

const ACTIONS_WITH_ICONS: Action["kind"][] = ["undo", "redo"];
const HOVER_SCROLL_INTERVAL_MS = 10;
const HOVER_SCROLL_STEP_PX = 20;

interface ActionPaletteProps {
  actions?: Action[],
  targetText?: string,
  onAction: (action: Action, invokation: ActionInvocation) => void,
  ref: Ref<HTMLDivElement>,
}

export const ActionPalette: VoidComponent<ActionPaletteProps> = (props) => {
  const tooltip = useGlobalTooltip()!;

  const [buttonsOverflow , setButtonsOverflow] =
    createSignal(false);
  const [buttonsScrollPosition , setButtonsScrollPosition] =
    createSignal<GeneralScrollPosition>("start");

  let buttonsEl!: HTMLDivElement;
  let leftScrollerEl!: HTMLDivElement;
  let rightScrollerEl!: HTMLDivElement;

  const handleButtonClick = props.onAction;

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

  return <Root id="action-palette" ref={props.ref}>
    <LeftFade
      style={{
        "display": buttonsScrollPosition() !== "start"
          ? "initial"
          : "none"
      }}
    >
      <FadeIcon />
      <Scroller ref={leftScrollerEl}/>
    </LeftFade>
    <RightFade
      style={{
        "display": buttonsOverflow() && buttonsScrollPosition() !== "end"
          ? "initial"
          : "none"
      }}
    >
      <FadeIcon />
      <Scroller ref={rightScrollerEl}/>
    </RightFade>
    <div
      class={ButtonsClass}
      use:onGeneralHorizontalScrollPositionChange={
        (position: GeneralScrollPosition) => setButtonsScrollPosition(position)
      }
      ref={el => buttonsEl = el}
    >
      <For each={props.actions}>{action => {
        const hasIcon = ACTIONS_WITH_ICONS.includes(action.kind);
        const actionKind = actionKinds[action.kind];
        return <div
          role="button"
          class={ButtonClass}
          classList={{
            [PaletteButtonClass]: true,
            [PaletteButtonDisabledClass]: action.disabled,
          }}
          use:holdClickEvent={
            action.disabled
            ? undefined
            : {
              durationMS: 400,
              holdClickCb: actionKind.hasAlternativeInvocation
                ? () => handleButtonClick(action, "alternative")
                : undefined,
              regularClickCb: () => handleButtonClick(action, "base"),
            }
          }
          data-tooltip-placement-override="right"
          use:tooltipAnchor={
            (!action.disabled && actionKind.description)
            ? {
              tooltip,
              header: hasIcon ? labelText(action, props.targetText) : undefined,
              body: actionKind.description,
            }
            : undefined
          }
        >
          <Show when={hasIcon} fallback={<PaletteButtonLabel innerHTML={labelText(action, props.targetText)} />}>
            <DefaultIcon iconName={action.kind} sizePx={34} />
          </Show>
        </div>;
      }}</For>
    </div>
  </Root>;
};

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
  background: linear-gradient(270deg, var(--color-bg) 50%, rgba(0, 0, 0, 0) 100%);
`;

const FadeIcon: VoidComponent = () => <FadeIconRoot iconName="scroll-arrow" sizePx={32} />;
const FadeIconRoot = styled(DefaultIcon)`
  background: var(--color-med);
  position: absolute;
  right: 0;
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
    font-size: 0.85rem;
  }
`;

const ButtonClass = css`
  width: max-content;
  flex-shrink: 0;

  sup {
    line-height: 100%;
  }

  &${PaletteButtonDisabledClass} {
    opacity: 0.5;
    cursor: default;
    pointer-events: none;
  }
`;

function labelText(action: Action, targetText?: string): string {
  const staticLabel = actionKinds[action.kind].staticLabel;
  if (staticLabel) {
    return staticLabel;
  } else if (action.kind === "transform-selected") {
    return `${targetText!} ➞ ${action.into}`;
  }
  return "";
}
