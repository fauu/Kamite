export type EventNotification =
  | { kind: "chunk-add", data: { chunk: string } }
  | { kind: "window-mouseenter", data: { target: DOMEventTarget, relatedTarget?: DOMEventTarget } }
  | { kind: "window-mouseleave", data: { target: DOMEventTarget, relatedTarget?: DOMEventTarget } };

export type DOMEventTarget = {
  tagName: string,
  attributes: Record<string, string | null>,
};

export function domEventTargetFromElement(el?: HTMLElement): DOMEventTarget | undefined {
  if (!el) {
    return undefined;
  }
  return {
    tagName: el.tagName,
    attributes: Object.fromEntries(
      Array.from(el.attributes).map(entry => [entry.nodeName, entry.nodeValue])
    )
  };
}
