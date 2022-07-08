import { createSignal } from "solid-js";

export function createEmptySignal() {
  const [value, setValue] = createSignal(undefined, { equals: false });

  function set() {
    setValue(undefined); 
  }

  return [value, set];
}
