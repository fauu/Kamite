import type { ChunkText } from "./Text";
import type { ChunkTranslation } from "./Translation";

export type Chunk = {
  text: ChunkText,
  originalText?: string,
  translation?: ChunkTranslation,
  playbackTimeS?: number,
  selected: boolean,
  historyFlashing: boolean,
};

export function chunk(params: Omit<Chunk, "selected" | "historyFlashing">): Chunk {
  return { selected: false, historyFlashing: false, ...params };
}
