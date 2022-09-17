export type Notification =
  | { kind: "chunk-added", body: { chunk: string } };
