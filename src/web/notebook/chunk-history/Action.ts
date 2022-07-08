import type { ChunksState } from "~/chunk";

export type ChunkHistoryAction = "copy" | "copy-original" | "reset-selection";

export function availableChunkHistoryActions(chunks: ChunksState) {
  const res: ChunkHistoryAction[] = [];

  const info = chunks.selectionInfo();
  if (info.otherThanCurrentSelected) {
    res.push("copy");
    if (info.someHaveOriginalText) {
      res.push("copy-original");
    }
    res.push("reset-selection");
  }

  return res;
}
