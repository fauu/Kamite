import { type Backend, type EventName, makeMouseEventNotificationData } from "./backend";
import { toggleEventListener } from "./common";

export type EventNotifier = ReturnType<typeof createEventNotifier>;

interface CreateEventNotifierParams {
  backend: Backend,
}

export function createEventNotifier({ backend }: CreateEventNotifierParams) {
  const notifiersActive: Map<EventName, boolean> = new Map();

  const notify = backend.eventNotify.bind(backend);

  const shouldNotify = (eventName: EventName): boolean =>
    notifiersActive.get(eventName) || false;

  const update = (subscribedEvents: EventName[]) => {
    // Deactivate the active notifiers that are no longer subscribed
    for (const [eventName, active] of notifiersActive) {
      if (active && !subscribedEvents.includes(eventName)) {
        notifiersActive.set(eventName, false);
        toggleNotificationListener(eventName, false);
      }
    }

    // Activate the subscribed notifiers that are not active yet
    for (const eventName of subscribedEvents) {
      if (!notifiersActive.get(eventName)) {
        notifiersActive.set(eventName, true);
        toggleNotificationListener(eventName, true);
      }
    }
  };

  const notifyTabMouseenter = (event: MouseEvent) =>
    notify({ name: "tab-mouseenter", data: makeMouseEventNotificationData(event) });

  const notifyTabMouseleave = (event: MouseEvent) =>
    notify({ name: "tab-mouseleave", data: makeMouseEventNotificationData(event) });

  const notifyApprootMouseenter = (event: MouseEvent) =>
    notify({ name: "approot-mouseenter", data: makeMouseEventNotificationData(event) });

  const notifyApprootMouseleave = (event: MouseEvent) =>
    notify({ name: "approot-mouseleave", data: makeMouseEventNotificationData(event) });

  const toggleNotificationListener = (eventName: EventName, on: boolean) => {
    switch (eventName) {
      case "chunk-add":
        // Empty
        break;
      case "tab-mouseenter":
        toggleEventListener(document.documentElement, "mouseenter", notifyTabMouseenter, on);
        break;
      case "tab-mouseleave":
        toggleEventListener(document.documentElement, "mouseleave", notifyTabMouseleave, on);
        break;
      case "approot-mouseenter":
        toggleEventListener(window.root, "mouseenter", notifyApprootMouseenter, on);
        break;
      case "approot-mouseleave":
        toggleEventListener(window.root, "mouseleave", notifyApprootMouseleave, on);
        break;
    }
  };

  return {
    shouldNotify,
    update
  };
}
