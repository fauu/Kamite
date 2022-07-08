import { onCleanup } from "solid-js";

export function horizontalWheelScroll(el: HTMLElement, value: () => boolean) {
  if (!value()) {
    return;
  }

  const handleWheel = (event: WheelEvent) => {
    event.preventDefault();
    el.scrollLeft += event.deltaY / 2;
  };

  el.addEventListener("wheel", handleWheel);

  onCleanup(() => {
    el.removeEventListener("wheel", handleWheel);
  });
}
