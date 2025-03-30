import type {
  InRecognizerOcrConfiguration, InRecognizerOcrConfigurationStatusKind, InRecognizerStatus,
  InRecognizerStatusKind,
} from "./InMessage";

import { parseBackendConstant } from "..";

export type RecognizerStatus = {
  kind: RecognizerStatusKind,
  availableCommands?: string[],
  ocrConfigurations?: OcrConfiguration[],
};

export type RecognizerStatusKind = Lowercase<InRecognizerStatusKind>;

export type OcrConfiguration = {
  name: string,
  status: OcrConfigurationStatus,
};

export type OcrConfigurationStatus = {
  kind: OcrConfigurationStatusKind,
  msg?: string,
};

export type OcrConfigurationStatusKind = Lowercase<InRecognizerOcrConfigurationStatusKind>;

export function parseRecognizerStatus(inStatus: InRecognizerStatus): RecognizerStatus {
  return {
    kind: parseBackendConstant(inStatus.kind) as RecognizerStatusKind,
    availableCommands: inStatus.availableCommands ?? undefined,
    ocrConfigurations: inStatus.ocrConfigurations.map(parseRecognizerStatusOcrConfiguration),
  };
}

function parseRecognizerStatusOcrConfiguration(inOcrConfiguration: InRecognizerOcrConfiguration) {
  return {
    ...inOcrConfiguration,
    status: {
      ...inOcrConfiguration.status,
      kind: parseBackendConstant(inOcrConfiguration.status.kind) as OcrConfigurationStatusKind
    },
  }
}
