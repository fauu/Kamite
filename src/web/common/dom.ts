export const LAYOUT_SHIFT_HANDLER_DEBOUNCE_DEFAULT_MS = 25;

export function scrollToBottom(el: HTMLElement) {
  el.scrollTop = el.scrollHeight;
}

export function domRectsOverlap(a?: DOMRect, b?: DOMRect): boolean {
  if (!a || !b) {
    return false;
  }
  return a.left <= b.right
    && a.right >= b.left
    && a.top <= b.bottom
    && a.bottom >= b.top;
}

export function getClientHeight(): number {
  return document.documentElement.clientHeight;
}

export const toggleEventListener = <K extends keyof HTMLElementEventMap>(
  el: HTMLElement,
  type: K,
  listener: (this: HTMLElement, ev: HTMLElementEventMap[K]) => any,
  on: boolean,
) => {
  on ? el.addEventListener(type, listener)
     : el.removeEventListener(type, listener);
}

