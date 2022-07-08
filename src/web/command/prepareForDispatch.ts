import type { Command, CustomCommand } from "~/backend";
import type { ChunksState } from "~/chunk";

export function commandPrepareForDispatch(command: Command, chunks: ChunksState): Command {
  if (command.kind === "other_custom") {
    customCommandFillPlaceholders(command as CustomCommand, chunks);
  }
  return command;
}

function customCommandFillPlaceholders(command: CustomCommand, chunks: ChunksState) {
  command.params.command = command.params.command.map(seg => {
    const trimmed = seg.trim();
    switch (trimmed) {
      case "{effectiveText}":
        return `${chunks.effectiveText()}`;
      case "{originalEffectiveText}":
        return `${chunks.originalEffectiveText()}`;
    }
    return seg;
  });
}
