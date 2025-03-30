import { Accessor, createEffect, onCleanup, type JSX } from "solid-js";

import { DEFAULT_TOOLTIP_SCALE, type Tooltip } from "~/common/floating";

export type TooltipAnchorParams = {
  tooltip: Tooltip,
  header?: JSX.Element,
  body?: JSX.Element,
  delayMS?: number,
  scale?: number,
  forceHide?: Accessor<boolean>,
};

export function tooltipAnchor(el: HTMLElement, value: () => TooltipAnchorParams | undefined) {
  const val = value();
  if (!val) {
    return;
  }
  const { tooltip, header, body, delayMS, scale } = val;
  const { setHeader, setBody, setScale, show, hide, floating } = tooltip;

  const mouseLeftSinceHideForced = false;

  createEffect(() => {
    if (val.forceHide) {
      if (val.forceHide()) {
        hide();
      } else {
        if (!mouseLeftSinceHideForced) {
          doShow();
        }
      }
    }
  });

  const handleMouseEnter = () => {
    if (val.forceHide && val.forceHide()) {
      return;
    }
    doShow();
  };

  const handleMouseLeave = () => {
    hide();
  };

  const doShow = () =>
    show(delayMS, () => {
      setHeader(() => header);
      setBody(() => body);
      setScale(scale || DEFAULT_TOOLTIP_SCALE);
      floating.setReference(el);
      tooltip.floating.update();
    });

  el.addEventListener("mouseenter", handleMouseEnter);
  el.addEventListener("mouseleave", handleMouseLeave);

  onCleanup(() => {
    el.removeEventListener("mouseenter", handleMouseEnter);
    el.removeEventListener("mouseleave", handleMouseLeave);
    hide();
  });
}

