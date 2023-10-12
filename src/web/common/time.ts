export const MSECS_IN_SECS = 1000;
export const MINS_IN_HOUR = 60;
export const SECS_IN_MIN = 60;

export type Time = {
  h: number,
  m: number,
};

export function defaultTime(): Time {
  return { h: 0, m: 0 };
}

export function timeToMins(time: Time): number {
  return (time.h * 60) + time.m;
}

export function timeEquals(a: Time, b: Time): boolean {
  return a.h === b.h && a.m === b.m;
}
