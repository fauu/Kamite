import type { ConfigCustomCommand, OCRRegion } from "..";

type OCRCommand =
  | typeof PARAMLESS_OCR_COMMANDS[number]
  | OCRAutoBlockCommand
  | OCRRegionCommand;

export const PARAMLESS_OCR_COMMANDS = [
  { kind: "ocr_manual-block" },
  { kind: "ocr_manual-block-vertical" },
  { kind: "ocr_manual-block-horizontal" },
  { kind: "ocr_manual-block-rotated" },
] as const;

export type OCRAutoBlockCommand = {
  kind: "ocr_auto-block",
  params: {
    mode: "select" | "instant",
  },
};

export type OCRRegionCommand = {
  kind: "ocr_region",
  params?: {
    x: number,
    y: number,
    width: number,
    height: number,
    autoNarrow: boolean,
  },
};

export const BASE_PLAYER_COMMANDS = [
  { kind: "player_playpause" },
];

export const PLAYER_COMMANDS = [
  { kind: "player_seek-start-sub" },
  { kind: "player_seek-back" },
  ...BASE_PLAYER_COMMANDS,
  { kind: "player_seek-forward" },
] as const;
type PlayerCommand = typeof PLAYER_COMMANDS[number];

type CharacterCounterCommand =
  | { kind: "character-counter_toggle-freeze" }
  | { kind: "character-counter_reset" };

type SessionTimerCommand =
  | { kind: "session-timer_start" }
  | { kind: "session-timer_stop" }
  | { kind: "session-timer_toggle" }
  | { kind: "session-timer_reset" };

type ChunkCommand =
  { kind: "chunk_show", params: { chunk: string } };

type OtherCommand = CustomCommand;

export type CustomCommand =
  { kind: "misc_custom", params: { command: string[] } };

export type Command =
  OCRCommand
  | PlayerCommand
  | CharacterCounterCommand
  | SessionTimerCommand
  | ChunkCommand
  | OtherCommand;

export function commandFromOCRRegion(r: OCRRegion): OCRRegionCommand {
  return {
    kind: "ocr_region" as const,
    params: {
      x: r.x,
      y: r.y,
      width: r.width,
      height: r.height,
      autoNarrow: r.autoNarrow,
    },
  };
}

export function commandFromConfigCustomCommand(c: ConfigCustomCommand): CustomCommand {
  return {
    kind: "misc_custom" as const,
    params: { command: c.command }
  };
}
