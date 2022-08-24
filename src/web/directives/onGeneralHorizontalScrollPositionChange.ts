import { onCleanup } from "solid-js";

export type GeneralScrollPosition = "start" | "middle" | "end";

export type OnGeneralHorizontalScrollPositionChangeParams =
  (position: GeneralScrollPosition) => void;

export function onGeneralHorizontalScrollPositionChange(
  el: HTMLElement, value: () => OnGeneralHorizontalScrollPositionChangeParams | undefined
) {
  const val = value();
  if (!val) {
    return;
  }

  const handleScroll = (event: Event) => {
    const t = event.target as HTMLDivElement;
    let pos: GeneralScrollPosition;
    if (t.scrollLeft === 0) {
      pos = "start";
    } else if (Math.ceil(t.scrollWidth - t.scrollLeft) === t.clientWidth) {
      pos = "end";
    } else {
      pos = "middle";
    }
    val(pos);
  };

  el.addEventListener("scroll", handleScroll);

  onCleanup(() => {
    el.removeEventListener("scroll", handleScroll);
  });
}
