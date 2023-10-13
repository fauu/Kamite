import { flip, offset, shift } from "@floating-ui/dom";
import { createSignal, type JSX } from "solid-js";

import { overridePlacement } from "./overridePlacement";
import { useFloating } from "./useFloating";

export const DEFAULT_TOOLTIP_SCALE = 1;

const OFFSET = 5;
const PADDING = 5;
const DEFAULT_REVEAL_DELAY_MS = 800;

export type Tooltip = ReturnType<typeof useTooltip>;

export function useTooltip() {
  const [visible, setVisible] = createSignal(false);
  const [revealTimeout, setRevealTimeout] = createSignal<number>();
  const [header, setHeader] = createSignal<JSX.Element>();
  const [body, setBody] = createSignal<JSX.Element>();
  const [scale, setScale] = createSignal(DEFAULT_TOOLTIP_SCALE);

  const floating = useFloating({
    middleware: [offset(OFFSET), overridePlacement, flip(), shift({ padding: PADDING })],
  });

  function show(delayMS: number = DEFAULT_REVEAL_DELAY_MS, pre?: () => void) {
    setRevealTimeout(window.setTimeout(() => {
      if (pre) {
        pre();
      }
      setVisible(true);
    }, delayMS));
  }

  function hide() {
    setVisible(false);
    if (revealTimeout()) {
      clearTimeout(revealTimeout());
    }
  }

  return {
    visible,
    header,
    setHeader,
    body,
    setBody,
    scale,
    setScale,
    floating,
    show,
    hide,
  };
}
