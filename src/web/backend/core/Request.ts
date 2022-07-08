import type { InMessage } from "./InMessage";

export type Request = ReuqestBase & RequestMain;

type ReuqestBase = { timestamp: number };

export type RequestMain = {
  kind: "add-furigana",
  body: { text: string },
};

export const requestKindToResponseKind: { [Key in Request["kind"]]: InMessage["kind"] } = {
  "add-furigana": "chunk-with-furigana",
};
