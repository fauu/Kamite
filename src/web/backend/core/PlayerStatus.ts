export type PlayerStatus = "disconnected" | "playing" | "paused";

export function playerStatusGotConnected(prev: PlayerStatus, next: PlayerStatus): boolean {
  return prev === "disconnected" && next !== prev;
}
