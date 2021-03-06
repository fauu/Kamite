import type { Config, UILayout } from "~/backend";

export type Setting = SettingBase & SettingMain;

export type SettingBase = {
  id : string,
  label: string,
  configKey: (c: Config) => Setting["value"],
  warning?: SettingWarning,
};

export type SettingMain = ToggleSettingMain | SelectSettingMain;

export type ToggleSettingMain = {
  kind: "toggle",
  value: boolean,
};

export type SelectSettingMain = {
  kind: "select",
  options: SelectOption[],
  value: SelectValue,
};

type SelectOption = {
  label: string,
  value: SelectValue,
};

export type SelectValue = UILayout;

// POLISH: The type could potentially be inferred here
export function getSetting<T extends Setting["value"]>(
  settings: Setting[], id: Setting["id"]
) : T | undefined {
  return settings.find(s => s.id === id)?.value as T;
}

export type SettingWarning = {
  text: string,
  show: (value: any) => boolean,
};
