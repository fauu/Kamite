import { onCleanup } from "solid-js";

export type HoldClickEventParams = {
  durationMS: number,
  holdClickCb?: (el: HTMLElement) => void,
  regularClickCb?: (el: HTMLElement) => void,
};

export function holdClickEvent(el: HTMLElement, value: () => HoldClickEventParams) {
  const val = value();
  if (!val) {
    return;
  }
  const { durationMS, holdClickCb, regularClickCb } = val;

  let holdTimeout: number | undefined;
  let handleMouseUp: () => void;
  let handleMouseDown: (() => void) | undefined;

  if (!holdClickCb) {
    if (!regularClickCb) {
      return;
    }
    handleMouseUp = () => regularClickCb(el);
  } else {
    handleMouseUp = () => {
      if (holdTimeout) {
        window.clearTimeout(holdTimeout);
        regularClickCb && regularClickCb(el);
      }
    };
    handleMouseDown = () => {
      holdTimeout = window.setTimeout(() => {
        holdClickCb(el);
        holdTimeout = undefined;
      }, durationMS);
    };
  }

  handleMouseDown && el.addEventListener("mousedown", handleMouseDown);
  el.addEventListener("mouseup", handleMouseUp);

  onCleanup(() => {
    handleMouseDown && el.removeEventListener("mousedown", handleMouseDown);
    el.removeEventListener("mouseup", handleMouseUp);
  });
}
