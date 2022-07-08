import type { Command } from "./Command";
import type { Request } from "./Request";

export type OutMessage = CommandMessage | RequestMessage;

export type CommandMessage = {
  kind: "command",
  body: Command,
};

export type RequestMessage = {
  kind: "request",
  body: Request,
};
