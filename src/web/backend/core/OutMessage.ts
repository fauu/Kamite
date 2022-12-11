import type { Command } from "./Command";
import type { Request } from "./Request";
import type { EventNotification } from "./Event";

export type OutMessage = CommandMessage | RequestMessage | EventNotificationMessage;

export type CommandMessage = {
  kind: "command",
  body: Command,
};

export type RequestMessage = {
  kind: "request",
  body: Request,
};

export type EventNotificationMessage = {
  kind: "event-notification",
  body: EventNotification,
};
