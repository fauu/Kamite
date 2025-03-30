import { Accessor, createEffect, createSignal, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { DefaultIcon, Dropdown } from "~/common";
import { RecognizerStatus } from "~/backend";

import { StatusPanelIndicator } from "./Indicator";

interface OcrConfigurationSelectorProps {
  recognizerStatus: Accessor<RecognizerStatus>,
  onChange: (configurationName: string) => void,
}

export const OcrConfigurationSelector: VoidComponent<OcrConfigurationSelectorProps> = props => {
  const [selectedValue, setSelectedValue] = createSignal<string>();

  const [dropdownOpen, setDropdownOpen] = createSignal(false);

  const options = () => {
    const configurations = props.recognizerStatus().ocrConfigurations;
    if (!configurations) {
      return [];
    }
    const res = configurations.map(c =>
      ({ value: c.name, label: c.name, disabled: c.status.kind !== "available" })
    );
    if (!selectedValue() && res.length > 0) {
      const val = res[0].value;
      setSelectedValue(val);
      props.onChange(val);
    }
    return res;
  }

  createEffect(() => {
    const val = selectedValue();
    val && props.onChange(val);
  });

  return <StatusPanelIndicator
    tooltipHeader="Active OCR configuration"
    tooltipBody=""
    forceHideTooltip={dropdownOpen}
    id="ocr-configuration-selector"
  >
    <Icon iconName="ocr-configurations" sizePx={18} />
    <Dropdown
      options={options()}
      value={selectedValue()}
      onChange={setSelectedValue}
      onOpen={() => setDropdownOpen(true)}
      onClose={() => setDropdownOpen(false)}
      class={DropdownExtraClass}
    />
  </StatusPanelIndicator>;
};

const DropdownExtraClass = css``;

const Icon = styled(DefaultIcon)`
  margin-right: 0.5rem;
`;
