import { onCleanup } from "solid-js";

import { scrollToBottom } from "~/common";

export type ScrollToBottomOnChildListChangeParams = {
  scrollParent?: boolean,
  delay?: boolean,
};

const DELAY_MS = 60;

export function scrollToBottomOnChildListChange(
  el: HTMLElement, value: () => ScrollToBottomOnChildListChangeParams | undefined
) {
  const val = value();
  if (!val) {
    return;
  }

  const observer = new MutationObserver((mutations) => {
    const scrollEl = val.scrollParent ? el.parentElement! : el;
    mutations.forEach(m => {
      if (m.addedNodes.length > 0 || m.removedNodes.length > 0) {
        if (val.delay) {
          setTimeout(() => scrollToBottom(scrollEl), DELAY_MS);
        } else {
          scrollToBottom(scrollEl);
        }
      }
    });
  });
  observer.observe(el, { childList: true });

  onCleanup(() => {
    observer.disconnect();
  });
}
