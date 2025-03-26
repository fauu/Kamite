import type { Command, Config, PlayerStatus, RecognizerStatus } from "~/backend";
import {
  BASE_PLAYER_COMMANDS, commandFromConfigCustomCommand, commandFromOCRRegion,
  PARAMLESS_OCR_COMMANDS, PLAYER_COMMANDS
} from "~/backend";
import type { ChunksState } from "~/chunk";

export type CommandPaletteCommand = {
  command: Command,
  enabled: boolean,
  symbol?: string,
  description?: string,
};

const DEFAULT_OCR_AUTO_BLOCK_COMMAND = { kind: "ocr_auto-block", params: { mode: "select" } };

export function availableCommandPaletteCommands(
  chunks: ChunksState,
  recognizerStatus: RecognizerStatus,
  mediaPlayerStatus: PlayerStatus,
  config?: Config,
): CommandPaletteCommand[] {
  const res: CommandPaletteCommand[] = [];

  if (["idle", "awaiting-user-input", "processng"].includes(recognizerStatus.kind)) {
    const enabled = recognizerStatus.kind === "idle";

    recognizerStatus.availableCommands?.forEach(kind => {
      if (PARAMLESS_OCR_COMMANDS.some(c => c.kind === kind)) {
        res.push(paletteCommand({ command: { kind }, enabled }));
      } else if (kind === "ocr_auto-block") {
        res.push(paletteCommand({ command: DEFAULT_OCR_AUTO_BLOCK_COMMAND, enabled }));
      } else if (kind === "ocr_region") {
        if (config?.ocr.regions) {
          res.push(
            ...config.ocr.regions.map(r =>
              paletteCommand({ command: commandFromOCRRegion(r), symbol: r.symbol, enabled })
            )
          );
        }
      } else {
        console.error("Notified of the availability of an unknown command:", kind);
      }
    });
  }

  if (mediaPlayerStatus !== "disconnected" && !chunks.textSelection.inProgress()) {
    const commands = config?.commands.player.showExtra
      ? PLAYER_COMMANDS
      : BASE_PLAYER_COMMANDS;
    res.unshift(...commands.map(command => paletteCommand({ command })));
  }

  if (config?.commands.custom) {
    res.push(
      ...config.commands.custom.map(c =>
        paletteCommand({
          command: commandFromConfigCustomCommand(c),
          symbol: c.symbol,
          description: c.name,
        })
      )
    );
  }

  return res;
}

function paletteCommand(
  pc: Omit<CommandPaletteCommand, "enabled"> & Pick<Partial<CommandPaletteCommand>, "enabled">
): CommandPaletteCommand {
  return {
    command: pc.command,
    enabled: pc.enabled ?? true,
    symbol: pc.symbol,
    description: pc.description ?? description(pc.command),
  };
}

function description(command: Command): string | undefined {
  switch (command.kind) {
    case "ocr_manual-block":
      return "OCR manually-selected area";
    case "ocr_manual-block-rotated":
      return "OCR manually-selected rotated area";
    case "ocr_auto-block":
      return "OCR autodetected text block";
    case "player_seek-start-sub":
      return "Seek to start of current subtitle";
    default:
      return undefined;
  }
}
