import { onCleanup } from "solid-js";

export type HoldClickEventParams = {
  durationMS: number,
  holdClickCb?: (el: HTMLElement) => void,
  regularClickCb?: (el: HTMLElement) => void,
};

export function holdClickEvent(el: HTMLElement, value: () => HoldClickEventParams) {
  const { durationMS, holdClickCb, regularClickCb } = value();

  if (!holdClickCb) {
    return;
  }

  let timeout: number | undefined;

  const handleMouseDown = () => {
    timeout = window.setTimeout(() => {
      holdClickCb(el);
      timeout = undefined;
    }, durationMS);
  };

  const handleMouseUp = () => {
    if (timeout) {
      window.clearTimeout(timeout);
      regularClickCb && regularClickCb(el);
    }
  };

  el.addEventListener("mousedown", handleMouseDown);
  el.addEventListener("mouseup", handleMouseUp);

  onCleanup(() => {
    el.removeEventListener("mousedown", handleMouseDown);
    el.removeEventListener("mouseup", handleMouseUp);
  });
}
