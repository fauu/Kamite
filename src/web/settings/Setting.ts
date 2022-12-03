import type { SetStoreFunction } from "solid-js/store";

import type { Config, UILayout } from "~/backend";

export type Setting = SettingBase & SettingMain;

export type SettingBase = {
  id : string,
  label: string,
  configKey: (c: Config) => Setting["value"],
  warning?: SettingWarning,
  help?: string,
  childIds?: Setting["id"][],
  child?: true,
  disabled?: { value: true, msg: string | undefined } | { value: false, msg: undefined },
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
  return settings.find(s => s.id === id)!.value as T;
}

// QUAL: Should the functions dealing with the entire store be moved?
export function disableSetting(
  settings: Setting[],
  id: Setting["id"],
  setSettings: SetStoreFunction<Setting[]>,
  msg?: string,
) {
  const setting = settings.find(s => s.id === id);
  if (!setting) {
    return;
  }
  setSettings(s => s.id === id, {
    "disabled": { value: true, msg },
    "value": false
  });
  updateChildSettingsDisabled(setting, setSettings);
}

export function updateChildSettingsDisabled(
  setting: Setting, setSettings: SetStoreFunction<Setting[]>
) {
  if (setting.childIds) {
    setSettings(s => setting.childIds!.includes(s.id), {
      "disabled": { value: !setting.value, msg: undefined }
    });
  }
}

export type SettingWarning = {
  text: string,
  show: (value: any) => boolean,
};
