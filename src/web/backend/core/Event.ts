export type EventNotification =
  | { name: "chunk-add", data: { chunkText: string } }
  | { name: "tab-mouseenter", data: MouseEventNotificationData }
  | { name: "tab-mouseleave", data: MouseEventNotificationData }
  | { name: "approot-mouseenter", data: MouseEventNotificationData }
  | { name: "approot-mouseleave", data: MouseEventNotificationData };

export type EventName = EventNotification["name"];

type MouseEventNotificationData = { target: DOMEventTarget, relatedTarget?: DOMEventTarget };

export type DOMEventTarget = {
  tagName: string,
  attributes: Record<string, string | null>,
};

function domEventTargetFromElement(el?: HTMLElement): DOMEventTarget | undefined {
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

export function makeMouseEventNotificationData(
  browserEvent: MouseEvent
): MouseEventNotificationData {
  return {
    target: domEventTargetFromElement(browserEvent.target! as HTMLElement)!,
    relatedTarget: domEventTargetFromElement(browserEvent.relatedTarget as HTMLElement)
  };
}
