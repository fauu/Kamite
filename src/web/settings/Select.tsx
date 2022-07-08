import { For, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import type { SettingsFieldMainProps } from "./FieldMainProps";
import type { SelectSettingMain } from "./Setting";

export const SettingsSelect:
  VoidComponent<SettingsFieldMainProps<SelectSettingMain, HTMLSelectElement>> = (props) =>
    <Root
      id={props.setting.id}
      onChange={props.onChange}
    >
      <For each={props.setting.options}>{o =>
        <option value={o.value} selected={o.value === props.setting.value}>
          {o.label}
        </option>
      }</For>
    </Root>;

const Root = styled.select`
  background: var(--color-bg3);
  font-weight: 500;
  border: 1px solid var(--color-bg3-hl);
  border-radius: var(--border-radius-default);
  padding: 0.28rem;
  color: var(--color-fg);
  font-size: 0.9rem;
  height: var(--form-control-height);
`;
