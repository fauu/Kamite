import { createStore } from "solid-js/store";
import type { DefaultTheme } from "solid-styled-components";

import type { UILayout } from "~/backend";

export type ThemeLayout = UILayout;

export function createTheme() {
  return createStore<DefaultTheme>({ layout: "STANDARD" });
}

export function themeLayoutFlipped(theme?: DefaultTheme): boolean {
  return theme ? theme.layout === "STANDARD_FLIPPED" : false;
}
