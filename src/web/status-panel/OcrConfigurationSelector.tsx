import { createEffect, createSignal, type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { Dropdown } from "~/common";

import { StatusPanelIndicator } from "./Indicator";

const configurations = [
  { value: 'opt1', label: 'Option 1' },
  { value: 'opt2', label: 'Option 2', disabled: true },
  { value: 'opt3', label: 'Option 3' },
];

export const OcrConfigurationSelector: VoidComponent = () => {
  const [selectedValue, setSelectedValue] = createSignal("opt1");

  const [dropdownOpen, setDropdownOpen] = createSignal(false);

  return <StatusPanelIndicator
    tooltipHeader="Active OCR configuration"
    tooltipBody=""
    forceHideTooltip={dropdownOpen}
    id="ocr-configuration-selector"
  >
    <Dropdown
      options={configurations}
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