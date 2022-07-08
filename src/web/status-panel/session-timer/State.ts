import { batch, createSignal } from "solid-js";

import type { SessionTimer } from "~/backend";

const UPDATE_INTERVAL_MS = 1000;
const MSECS_IN_SECS = 1000;
const MINS_IN_HOUR = 60;
const SECS_IN_MIN = 60;

type Time = {
  h: number,
  m: number,
};

export type SessionTimerState = ReturnType<typeof createSessionTimerState>;

export function createSessionTimerState() {
  const [time, setTime] = createSignal<Time>({ h: 0, m: 0 });
  const [running, setRunning] = createSignal(false);
  const [updateInterval, setUpdateInterval] = createSignal<ReturnType<typeof window.setInterval>>();

  function sync({ accumulatedTime, running, currentStartTime }: SessionTimer) {
    batch(() => {
      if (updateInterval()) {
        clearInterval(updateInterval());
      }

      setRunning(running);
      if (!running) {
        update(accumulatedTime);
        return;
      }

      const tick = () => update(accumulatedTime, new Date(currentStartTime));
      setUpdateInterval(setInterval(tick, UPDATE_INTERVAL_MS));
    });
  }

  function update(accumulatedTimeS: number, startTime?: Date) {
    let s = accumulatedTimeS;
    if (startTime) {
      s += ((Date.now() - +startTime) / MSECS_IN_SECS);
    }
    const h = Math.floor(s / (MINS_IN_HOUR * SECS_IN_MIN));
    const m = Math.floor(s / SECS_IN_MIN) % MINS_IN_HOUR;
    setTime({ h, m });
  }

  return {
    time,
    // setTime,
    running,
    // setRunning,

    sync,
  };
}
