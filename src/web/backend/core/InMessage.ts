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
| ChunkWithFuriganaMessage
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

// XXX
export type ChunkWithFuriganaMessage = {
  kind: "chunk-with-furigana",
  chunkWithFurigana: ChunkWithFurigana,
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
  maybeRubies: MaybeRuby[],
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

// XXX
export type ChunkWithFurigana = { maybeRubies: MaybeRuby[] };
export type MaybeRuby = { base: string, text: string | null };
