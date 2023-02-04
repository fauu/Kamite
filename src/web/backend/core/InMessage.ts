import type { Config } from "..";

export type InMessage =
| {
  kind: "response",
  requestTimestamp: number,
  innerMessage: InMessage,
}
| {
  kind: "user-notification",
  userNotificationKind: UserNotificationKind,
  content: string,
}
| {
  kind: "chunk-variants",
  variants: ChunkVariant[],
  playbackTimeS: number | null,
}
| {
  kind: "chunk-translation",
  translation: string,
  playbackTimeS: number | null,
}
| ChunkEnhancementsInMessage
| {
  kind: "program-status",
  subKind: "full",
} & ProgramStatus
| {
  kind: "program-status",
  subKind: string,
} & Partial<ProgramStatus>
| {
  kind: "debug-image",
  imgB64: string,
}
| {
  kind: "config",
  config: Config,
}
| {
  kind: "lookup-request",
  targetSymbol: string,
  customText: string | null,
};

export type ChunkEnhancementsInMessage = {
  kind: "chunk-enhancements",
  chunkEnhancements: ChunkEnhancements,
};

type UserNotificationKind = "INFO" | "ERROR";

export interface ChunkVariant {
  content: string,
  originalContent: string | null,
  labels: string[],
  score: number,
  enhancements: ChunkEnhancements,
}

export type ChunkEnhancements = {
  interVariantUniqueCharacterIndices: number[] | null,
  furiganaMaybeRubies: MaybeRuby[],
}

export interface ProgramStatus {
  debug: boolean,
  profileNames: string[],
  sessionTimer: SessionTimer,
  characterCounter: CharacterCounter,
  unavailableUniversalFeatures: UnavailableUniversalFeature[],
  recognizerStatus: InRecognizerStatus,
  playerStatus: PlayerStatus,
  subscribedEvents: string[],
}

export type SessionTimer =
  & { accumulatedTime: number }
  & (
    | { running: false, currentStartTime: null }
    | { running: true, currentStartTime: string }
  );

export type CharacterCounter = {
  count: number,
  frozen: boolean,
};

// QUAL: Move?
export function defaultCharacterCounter(): CharacterCounter {
  return { count: 0, frozen: false };
}

export type UnavailableUniversalFeature =
  | { id: "auto-furigana", reason: "KUROMOJI_UNAVAILABLE" };

export type InRecognizerStatus = {
  kind: InRecognizerStatusKind,
  availableCommands: string[] | null,
};

export type InRecognizerStatusKind =
  | "UNAVAILABLE"
  | "INITIALIZING"
  | "IDLE"
  | "AWAITING_USER_INPUT"
  | "PROCESSING";

type PlayerStatus = "CONNECTED" | "DISCONNECTED" | "PAUSED" | "UNPAUSED";

export type MaybeRuby = { base: string, text: string | null };
