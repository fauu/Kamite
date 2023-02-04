import { batch, createSignal } from "solid-js";

import type { SessionTimer } from "~/backend";
import { MINS_IN_HOUR, MSECS_IN_SECS, SECS_IN_MIN } from "~/common/time";

const UPDATE_INTERVAL_MS = 1000;

type Time = {
  h: number,
  m: number,
};

function defaultTime(): Time {
  return { h: 0, m: 0 };
}

export type SessionTimerState = ReturnType<typeof createSessionTimerState>;

export function createSessionTimerState() {
  const [time, setTime] = createSignal<Time>(defaultTime());
  const [running, setRunning] = createSignal(false);
  const [autoPauseIntervalS, setAutoPauseIntervalS] = createSignal<number | undefined>(undefined);
  const [autoPaused, setAutoPaused] = createSignal(false);
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
    autoPauseIntervalS,
    setAutoPauseIntervalS,
    autoPaused,
    setAutoPaused,
    running,
    // setRunning,

    sync,
  };
}
