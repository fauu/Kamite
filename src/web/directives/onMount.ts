import { onCleanup, onMount as solidOnMount } from "solid-js";

export type OnMountParams = {
  run: (el: HTMLElement) => void,
  cleanup?: (el: HTMLElement) => void,
};

export function onMount(el: HTMLElement, value: () => OnMountParams) {
  const { run, cleanup } = value();
  solidOnMount(() => {
    run(el);
    cleanup && onCleanup(() => cleanup(el));
  });
}
