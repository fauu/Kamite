import type { Command } from "./Command";
import type { Request } from "./Request";
import type { Notification } from "./Notification";

export type OutMessage = CommandMessage | RequestMessage | NotificationMessage;

export type CommandMessage = {
  kind: "command",
  body: Command,
};

export type RequestMessage = {
  kind: "request",
  body: Request,
};

export type NotificationMessage = {
  kind: "notification",
  body: Notification,
};
