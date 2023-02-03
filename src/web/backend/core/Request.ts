import type { InMessage } from "./InMessage";

export type Request = ReuqestBase & RequestMain;

type ReuqestBase = { timestamp: number };

export type RequestMain = {
  kind: "get-chunk-enhancements",
  body: { text: string, enhancements: ChunkEnhancement[] },
};

// QUAL: Move?
type ChunkEnhancement = "furigana";

export const requestKindToResponseKind: { [Key in Request["kind"]]: InMessage["kind"] } = {
  "get-chunk-enhancements": "chunk-enhancements",
};
