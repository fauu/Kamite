import { For, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { SettingsField, type InputChangeHandler } from "./Field";
import type { SelectValue, Setting } from "./Setting";

export const DEFAULT_SETTINGS: Setting[] = [
  {
    id: "layout",
    label: "Interface layout",
    configKey: (c) => c.ui.layout,
    kind: "select",
    options: [
      { label: "Standard",         value: "STANDARD" },
      { label: "Standard Flipped", value: "STANDARD_FLIPPED" }
    ],
    value: "STANDARD",
  },
  {
    id: "show-furigana",
    label: "Show furigana",
    configKey: (c) => c.chunk.showFurigana,
    kind: "toggle",
    value: false,
    warning: {
      text: "The furigana is auto-generated and frequently incorrect. Use cautiously",
      show: (value: boolean) => value,
    },
  },
  {
    id: "translation-only-mode",
    label: "Translation-only mode",
    configKey: (c) => c.chunk.translationOnlyMode,
    kind: "toggle",
    value: false,
    help: `Treat incoming chunks as translations and create a new empty chunk for each translation.
Useful when watching media with just the translation subtitles.`,
  },
];

interface SettingsProps {
  store: Setting[],
  onSettingChangeRequested: SettingChangeRequest,
}

export const Settings: VoidComponent<SettingsProps> = (props) => {
  const inputValueToSettingValue = (t: EventTarget, kind: Setting["kind"]): Setting["value"] => {
    switch (kind) {
      case "toggle":
        return (t as HTMLInputElement).checked;
      case "select":
        return (t as HTMLSelectElement).value as SelectValue;
    }
  };

  const handleInputChange: InputChangeHandler = (setting, event) => {
    if (!event.currentTarget) {
      return;
    }
    props.onSettingChangeRequested(
      setting.id,
      inputValueToSettingValue(event.currentTarget, setting.kind)
    );
  };

  return <Root id="settings">
    <Notice>
      Note: If you wish to preserve your changes, specify them in the config file.
    </Notice>
    <Main>
      <For each={props.store}>{s =>
        <SettingsField setting={s} onChange={(s, e) => handleInputChange(s, e)} />
      }</For>
    </Main>
  </Root>;
};

const Root = styled.div`
  flex-direction: column;
`;

const Notice = styled.span`
  font-size: 0.95rem;
  color: var(--color-fg3);
`;

const Main = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-top: 1rem;
`;

export type SettingChangeRequest = (id: Setting["id"], value: Setting["value"]) => void;
