import { Accessor } from "solid-js";

export {};

declare global {
  interface Window {
    root: HTMLDivElement,
    mainUIVisible: Accessor<boolean>,
  }
}
