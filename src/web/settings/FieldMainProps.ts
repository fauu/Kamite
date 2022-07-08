import { type JSX } from "solid-js";

import type { SettingBase, SettingMain } from "./Setting";

export interface SettingsFieldMainProps<S extends SettingMain, T extends HTMLElement> {
  setting: SettingBase & S,
  onChange?: JSX.EventHandler<T, Event>,
}
