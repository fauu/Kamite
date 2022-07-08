import { Match, Show, Switch, type JSX, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { ConfigKey } from "~/common";
import { LAYOUT_BREAKPOINT_SMALL } from "~/globalStyles";

import { SettingsSelect } from "./Select";
import type { SelectSettingMain, Setting, SettingBase, ToggleSettingMain } from "./Setting";
import { SettingsToggleClass, SettingsToggleMain } from "./ToggleMain";

interface SettingsFieldProps {
  setting: Setting,
  onChange: InputChangeHandler,
}

export const SettingsField: VoidComponent<SettingsFieldProps> = (props) => {
  // POLISH: There's a better way of doing this
  const admitToggle = (setting: Setting): (SettingBase & ToggleSettingMain) | false =>
      setting.kind === "toggle" ? setting : false;
  const admitSelect = (setting: Setting): (SettingBase & SelectSettingMain) | false =>
      setting.kind === "select" ? setting : false;

  const handleChange: JSX.EventHandler<HTMLElement, Event> = event =>
    props.onChange({ id: props.setting.id, kind: props.setting.kind }, event);

  return <Root>
    <Label
      class="issue-9"
      classList={{ [SettingsToggleClass]: props.setting.kind === "toggle" }}
    >
      <LabelText>
        <LabelTextMain>
          {props.setting.label}
        </LabelTextMain>
        <ConfigKey value={humanizeConfigKey(props.setting)} />
      </LabelText>
      <Switch>
        <Match when={admitToggle(props.setting)}>{s =>
          <SettingsToggleMain setting={s} onChange={handleChange} />
        }</Match>
        <Match when={admitSelect(props.setting)}>{s =>
          <SettingsSelect setting={s} onChange={handleChange} />
        }</Match>
      </Switch>
    </Label>
    <Show when={props.setting.warning && props.setting.warning.show(props.setting.value)}>
      <Warning>
        <WarningIcon/>
        <WarningText>{props.setting.warning!.text}</WarningText>
      </Warning>
    </Show>
  </Root>;
};

const Root = styled.div`
  display: inline-block;
  border: 1px dashed var(--color-bg3);
  border-radius: var(--border-radius-default);
  padding: 0.45rem 0.5rem;

  &:not(:last-child) {
    margin-bottom: 0.5rem;
  }

  ${LAYOUT_BREAKPOINT_SMALL} {
    padding-bottom: 0.5rem;
    width: 300px;
  }
`;

const Label = styled.label`
  display: flex;
  user-select: none;
  align-items: center;
`;

const LabelText = styled.div`
  width: 400px;
  display: flex;
  flex-direction: column;
`;

const LabelTextMain = styled.span`
  margin-right: 0.45rem;
`;

const Warning = styled.div`
  margin-top: 0.3rem;
`;

const WarningIcon = styled.span`
  display: inline-block;
  width: 16px;
  height: 16px;
  background: url('icons/warning.png');
  vertical-align: -2px;
  margin-right: 3px;
`;

const WarningText = styled.span`
  font-size: 0.9rem;
  color: var(--color-warning-hl);
`;

function humanizeConfigKey(s: Setting) {
  return s.configKey.toString()
    .replace(/[\s\S]*c\.(.*);[\s\S]*/, "$1")
    .replace(/.*=>.*?\./, "")
    .replaceAll("_", "-");
}

export type InputChangeHandler = (setting: Pick<Setting, "id" | "kind">, event: Event) => void;
