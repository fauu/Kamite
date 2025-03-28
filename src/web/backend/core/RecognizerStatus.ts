import type { InRecognizerStatus, InRecognizerStatusKind } from "./InMessage";

import { parseBackendConstant } from "..";

export type RecognizerStatus = {
  kind: RecognizerStatusKind,
  availableCommands?: string[],
  configurations?: OCRConfiguration[],
};

export type RecognizerStatusKind = Lowercase<InRecognizerStatusKind>;

export type OCRConfiguration = {
  name: string,
  status: OCRConfigurationStatus,
};

export type OCRConfigurationStatus = {
  kind: OCRConfigurationStatusKind,
  msg?: string,
};

export type OCRConfigurationStatusKind = Lowercase<OCRConfigurationStatusKind>;

export function parseRecognizerStatus(inStatus: InRecognizerStatus) {
  inStatus.configurations.forEach(c => c.status.kind = parseBackendConstant(c.status.kind));
  return {
    kind: parseBackendConstant(inStatus.kind) as RecognizerStatusKind,
    availableCommands: inStatus.availableCommands ?? undefined,
    configurations: inStatus.configurations,
  };
}
