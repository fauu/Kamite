import type { InRecognizerStatus, InRecognizerStatusKind } from "./InMessage";

import { parseBackendConstant } from "..";

export type RecognizerStatus = {
  kind: RecognizerStatusKind,
  availableCommands?: string[],
};

export type RecognizerStatusKind = Lowercase<InRecognizerStatusKind>;

export function parseRecognizerStatus(inStatus: InRecognizerStatus) {
  return {
    kind: parseBackendConstant(inStatus.kind) as RecognizerStatusKind,
    availableCommands: inStatus.availableCommands ?? undefined,
  };
}
