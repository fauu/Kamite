import { MINS_IN_HOUR, timeToMins, type Time } from "~/common/time";

const PACE_CPM_ROUNDING = 50;

export function approxPaceCPH(characterCount: number, sessionTime: Time): number | undefined {
  const sessionTimeMins = timeToMins(sessionTime);
  if (sessionTimeMins === 0) {
    return;
  }
  const paceCPH = characterCount / sessionTimeMins;
  const paceCPM = Math.round(paceCPH * MINS_IN_HOUR);
  const paceCPMRounded = Math.round(paceCPM / PACE_CPM_ROUNDING) * PACE_CPM_ROUNDING;
  return paceCPMRounded;
}
