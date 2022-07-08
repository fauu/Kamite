import type { Chunk } from "./Chunk";

// Maximum playback time delta in seconds with which two subsequent incoming chunks can be qualified
// as potential replay repeats
const MEDIA_PLAYER_REPEAT_PLAYBACK_TIME_MAX_DELTA_S = 0.8;

export function incomingChunkIsAReplay(
  incomingText: string,
  incomingPlaybackTimeS: number,
  current: Chunk
): boolean {
  return current.playbackTimeS !== undefined
    && Math.abs(incomingPlaybackTimeS - current.playbackTimeS)
        <= MEDIA_PLAYER_REPEAT_PLAYBACK_TIME_MAX_DELTA_S
    && incomingText === current.text.base;
}
