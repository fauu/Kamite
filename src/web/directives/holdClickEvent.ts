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
  let handleClick: (() => void) | undefined;
  let handleMouseUp: (() => void) | undefined;
  let handleMouseDown: (() => void) | undefined;

  if (!holdClickCb) {
    if (!regularClickCb) {
      return;
    }
    handleClick = () => regularClickCb(el);
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

  handleClick && el.addEventListener("click", handleClick);
  handleMouseDown && el.addEventListener("mousedown", handleMouseDown);
  handleMouseUp && el.addEventListener("mouseup", handleMouseUp);

  onCleanup(() => {
    handleClick && el.removeEventListener("click", handleClick);
    handleMouseDown && el.removeEventListener("mousedown", handleMouseDown);
    handleMouseUp && el.removeEventListener("mouseup", handleMouseUp);
  });
}
