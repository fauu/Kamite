import { type Accessor, createEffect, onCleanup } from "solid-js";
import { css } from "solid-styled-components";

export type ConcealUnlessHoveredParams = {
  enabled: Accessor<boolean>,
};

export function concealUnlessHovered(el: HTMLElement, value: () => ConcealUnlessHoveredParams) {
  if (!value) {
    return;
  }
  const { enabled } = value();

  let coverEl: HTMLElement | undefined;

  const handleMouseEnter = () => {
    toggleCoverVisibility(coverEl!, true);
  };

  const handleMouseLeave = (event: MouseEvent) => {
    if (event.relatedTarget === null) {
      // Ignore when cursor moved from Notebook's Settings page to a browser <select> dropdown
      return;
    }
    toggleCoverVisibility(coverEl!, false);
  };

  const handleDocumentBlur = () => {
    toggleCoverVisibility(coverEl!, false);
  };

  const cleanup = () => {
    if (coverEl) {
      el.removeChild(coverEl);
      coverEl = undefined;
    }
    handleMouseEnter && el.removeEventListener("mouseenter", handleMouseEnter);
    handleMouseLeave && el.removeEventListener("mouseleave", handleMouseLeave);
    handleDocumentBlur && document.removeEventListener("blur", handleDocumentBlur);
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
      document.addEventListener("blur", handleDocumentBlur);
    }
  });

  onCleanup(cleanup);
}

function toggleCoverVisibility(el: HTMLElement, visible: boolean) {
  el.classList.toggle(CoverHiddenClass, visible);
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
