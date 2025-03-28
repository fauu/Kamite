import { createEffect, createSignal, type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { Dropdown } from "~/common";

import { StatusPanelIndicator } from "./Indicator";

const configurations = [
  { value: 'opt1', label: 'Option 1' },
  { value: 'opt2', label: 'Option 2', disabled: true },
  { value: 'opt3', label: 'Option 3' },
];

interface OcrConfigurationSelectorProps {
  recognizerStatus: Accessor<RecognizerStatus>,
}

export const OcrConfigurationSelector: VoidComponent = props => {
  const [selectedValue, setSelectedValue] = createSignal();

  const [dropdownOpen, setDropdownOpen] = createSignal(false);

  const options = () => {
    const configurations = props.recognizerStatus().configurations;
    if (!configurations) {
      return [];
    }
    const res = configurations.map(c =>
      ({ value: c.name, label: c.name, disabled: c.status.kind !== "available" })
    );
    if (res.length > 0) {
      setSelectedValue(res[0].value);
    }
    return res;
  }

  return <StatusPanelIndicator
    tooltipHeader="Active OCR configuration"
    tooltipBody=""
    forceHideTooltip={dropdownOpen}
    id="ocr-configuration-selector"
  >
    <Dropdown
      options={options()}
      value={selectedValue()}
      onChange={setSelectedValue}
      onOpen={() => setDropdownOpen(true)}
      onClose={() => setDropdownOpen(false)}
      placeholder="Choose an option..."
      class={DropdownExtraClass}
    />
  </StatusPanelIndicator>;
};

// XXX
const DropdownExtraClass = css``;
