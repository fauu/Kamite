import { type Accessor, createEffect, onCleanup } from "solid-js";
import { css } from "solid-styled-components";

export type ConcealUnlessHoveredParams = {
  enabled: Accessor<boolean>,
};

// ROBUSTNESS: Conceal is mistakenly triggered when mouse moves into a browser dropdown (at least
//             on Firefox)
export function concealUnlessHovered(el: HTMLElement, value: () => ConcealUnlessHoveredParams) {
  const { enabled } = value();

  let coverEl: HTMLElement | undefined;

  const handleMouseEnter = () => toggleCoverVisibility(coverEl!, false);
  const handleMouseLeave = (event: MouseEvent) => {
    if (event.relatedTarget && !window.root.contains(event.relatedTarget as Node)) {
      // Ignore when leaving into Yomichan window etc.
      return;
    }
    toggleCoverVisibility(coverEl!, true);
  };

  const cleanup = () => {
    if (coverEl) {
      el.removeChild(coverEl);
      coverEl = undefined;
    }

    handleMouseEnter && el.removeEventListener("mouseenter", handleMouseEnter);
    if (handleMouseLeave) {
      el.removeEventListener("mouseleave", handleMouseLeave);
      document.documentElement.removeEventListener("mouseleave", handleMouseLeave);
    }
  };

  createEffect(() => {
    if (!enabled()) {
      cleanup();
    } else {
      coverEl = document.createElement("div");
      coverEl.classList.add(CoverClass);
      el.prepend(coverEl);

      el.addEventListener("mouseenter", handleMouseEnter);
      el.addEventListener("mouseleave", handleMouseLeave);
      document.documentElement.addEventListener("mouseleave", handleMouseLeave);
    }
  });

  onCleanup(cleanup);
}

function toggleCoverVisibility(el: HTMLElement, visible: boolean) {
  el.classList.toggle(CoverHiddenClass, !visible);
}

export const CoverClass = css`
  position: absolute;
  width: 100%;
  height: 100%;
  background: var(--color-bg);
  z-index: 50;
  transition: opacity var(--fade-transition-duration-default) ease-in;
`;

export const CoverHiddenClass = css`
  opacity: 0;
  pointer-events: none;
`;
