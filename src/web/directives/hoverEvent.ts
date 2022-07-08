import { onCleanup } from "solid-js";

export type HoverEventParams = {
  enterDelayMS: number,
  enterCb?: (el: HTMLElement) => void,
  leaveCb?: (el: HTMLElement) => void,
};

export function hoverEvent(el: HTMLElement, value: () => HoverEventParams) {
  const { enterDelayMS, enterCb, leaveCb } = value();

  if (!enterCb && !leaveCb) {
    return;
  }

  let timeout: number | undefined;

  const handleMouseenter = () => {
    timeout = window.setTimeout(() => {
      enterCb && enterCb(el);
      timeout = undefined;
    }, enterDelayMS);
  };

  const handleMouseleave = () => {
    leaveCb && leaveCb(el);
    if (timeout) {
      window.clearTimeout(timeout);
    }
  };

  el.addEventListener("mouseenter", handleMouseenter);
  el.addEventListener("mouseleave", handleMouseleave);

  onCleanup(() => {
    el.removeEventListener("mouseenter", handleMouseenter);
    el.removeEventListener("mouseleave", handleMouseleave);
  });
}
