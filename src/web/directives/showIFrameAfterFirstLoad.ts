import { onCleanup } from "solid-js";

export function showIFrameAfterFirstLoad(el: HTMLIFrameElement, value: () => boolean) {
  if (!value()) {
    return;
  }

  const cleanup = () => el.removeEventListener("load", handleLoad);

  const handleLoad = () => {
    if (el.src !== "") {
      el.style.display = "initial";
      cleanup();
    }
  }

  el.addEventListener("load", handleLoad);

  onCleanup(cleanup);
}
