import { Match, Show, Switch, type JSX, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { tooltipAnchor } from "~/directives";
const [_] = [tooltipAnchor];

import { ConfigKey, DefaultIcon } from "~/common";
import { useGlobalTooltip } from "~/GlobalTooltip";
import { LAYOUT_BREAKPOINT_SMALL } from "~/style";

import { SettingsSelect } from "./Select";
import type { SelectSettingMain, Setting, SettingBase, ToggleSettingMain } from "./Setting";
import { SettingsToggleClass, SettingsToggleMain } from "./ToggleMain";

interface SettingsFieldProps {
  setting: Setting,
  onChange: InputChangeHandler,
}

export const SettingsField: VoidComponent<SettingsFieldProps> = (props) => {
  const tooltip = useGlobalTooltip()!;

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
      classList={{
        [SettingsToggleClass]: props.setting.kind === "toggle",
        [DisabledFieldClass]: !!props.setting.disabled,
      }}
    >
      <Info>
        <InfoMain>
          {props.setting.label}
          <Show when={props.setting.help}>
            <span
              class={HelpIndicatorClass}
              use:tooltipAnchor={{
                tooltip,
                body: props.setting.help,
                delayMS: 300,
              }}
            />
          </Show>
        </InfoMain>
        <ConfigKey value={humanizeConfigKey(props.setting)} />
      </Info>
      <Switch>
        <Match when={admitToggle(props.setting)} keyed>{s =>
          <SettingsToggleMain setting={s} onChange={handleChange} />
        }</Match>
        <Match when={admitSelect(props.setting)} keyed>{s =>
          <SettingsSelect setting={s} onChange={handleChange} />
        }</Match>
      </Switch>
    </Label>
    <Show when={props.setting.disabled} keyed>{ disabled =>
      <DisabledMessage innerHTML={disabled.msg} />
    }</Show>
    <Show when={props.setting.warning && props.setting.warning.show(props.setting.value)}>
      <Warning>
        <WarningIcon iconName="warning" sizePx={16} />
        <WarningText>{props.setting.warning!.text}</WarningText>
      </Warning>
    </Show>
  </Root>;
};

export const DisabledFieldClass = "disabled";

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

  &.${DisabledFieldClass} {
    opacity: 0.4;
    pointer-events: none;
  }
`;

const Info = styled.div`
  width: 400px;
  display: flex;
  flex-direction: column;
`;

const InfoMain = styled.div`
  margin-right: 0.45rem;
  display: flex;
  align-items: center;
`;

const HelpIndicatorClass = css`
  padding: 0.01rem 0.2rem;
  margin-left: 0.28rem;
  color: var(--color-fg3);
  background: var(--color-bg3);
  font-size: 0.85rem;
  font-weight: 700;
  border-radius: var(--border-radius-default);

  &:after {
    content: "?";
  }
`;

const DisabledMessage = styled.div`
  color: var(--color-fg3);
  margin-top: 0.15rem;
  font-size: 0.95rem;
`;

const Warning = styled.div`
  margin-top: 0.3rem;
`;

const WarningIcon = styled(DefaultIcon)`
  display: inline-block;
  height: ${p => p.sizePx}px;
  background: var(--color-warning);
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
