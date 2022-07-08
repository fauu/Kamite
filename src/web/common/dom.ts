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
