import { css } from "solid-styled-components";

enum LabelSelectionMarkerId {
  Left = 0,
  Right = 1
}

export class LabelSelectionMarkers {
  #els: [HTMLDivElement, HTMLDivElement];

  constructor(parentEl: HTMLElement) {
    const start = document.createElement("div");
    start.classList.add(MarkerClass, StartMarkerClass);
    const end = document.createElement("div");
    end.classList.add(MarkerClass, EndMarkerClass);
    this.#els = [start, end];
    parentEl.append(...this.#els);
  }

  hide() {
    this.#els.forEach(el => el.style.display = "none");
  }

  showLeftMarkerAt(x: number, y: number) {
    this.#showMarkerAt(LabelSelectionMarkerId.Left, x, y);
  }

  showRightMarkerAt(x: number, y: number) {
    this.#showMarkerAt(LabelSelectionMarkerId.Right, x, y);
  }

  #showMarkerAt(markerId: LabelSelectionMarkerId, x: number, y: number) {
    this.#els[markerId].style.setProperty("--x", x.toString());
    this.#els[markerId].style.setProperty("--y", y.toString());
    this.#els[markerId].style.display = "block";
  }
}

const MarkerClass = css`
  --width: calc((var(--char-height) * 0.25) * 1px);
  --height: calc((var(--char-height) * 0.5) * 1px);
  --yPx: calc(var(--y) * 1px);
  --xPx: calc(var(--x) * 1px);

  width: var(--width);
  height: var(--height);
  position: fixed;
  border: 2px solid var(--color-accB);
  display: none;
  z-index: 10;
`;

const StartMarkerClass = css`
  top: var(--yPx);
  left: calc(var(--xPx));
  border-top-left-radius: var(--selection-border-radius);
  border-right: 0;
  border-bottom: 0;
`;

const EndMarkerClass = css`
  top: calc(var(--yPx) + var(--height));
  left: calc(var(--xPx) - var(--width));
  border-bottom-right-radius: var(--selection-border-radius);
  border-top: 0;
  border-left: 0;
`;

