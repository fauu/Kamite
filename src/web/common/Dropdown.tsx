import classNames from "classnames";
import { type Component, createSignal, createEffect, onCleanup, For, createMemo, Show } from "solid-js";
import { css, styled } from "solid-styled-components";

interface DropdownOption {
	value: any;
	label: string;
	disabled?: boolean;
}

interface DropdownProps {
	value?: any;
	placeholder?: string;
	options: DropdownOption[];
  optionListHeader?: string;
	onChange?: (value: any) => void;
	onOpen?: () => void;
	onClose?: () => void;
	class?: string;
	id?: string;
}

// XXX: Review
export const Dropdown: Component<DropdownProps> = props => {
  const [isOpen, setIsOpen] = createSignal(false);

  let dropdownRef: HTMLDivElement | undefined;

  // XXX: Use props.value directly instead of currentValue?
  // Determine the current value (controlled or internal)
  // Note: In Solid, directly using props.value in effects/memos is often sufficient
  // as props are reactive. No separate internal signal strictly needed for controlled.
  const currentValue = createMemo(() => props.value);

  const displayLabel = createMemo(() => {
    const currentVal = currentValue();
    const selectedOption = props.options?.find(opt => opt.value === currentVal);
    return selectedOption
      ? selectedOption.label
      : props.placeholder || "Select...";
  });

  const isValueSelected = createMemo(() => {
    return currentValue() !== undefined && currentValue() !== null;
  });

  const canSelectionChange = () => (props.options.length >= 2) || !isValueSelected();

  const handleSelectedValueDisplayClick = (event: MouseEvent) => {
    event.stopPropagation();

    setIsOpen(prev => {
      if (prev) return false;
      return canSelectionChange();
    });
  };

  const handleOptionClick = (option: DropdownOption) => {
    if (option.disabled) return;

    if (props.onChange) {
      props.onChange(option.value);
    }

    setIsOpen(false);
  };

  const handleClickOutside = (event: MouseEvent) => {
    if (dropdownRef && !dropdownRef.contains(event.target as Node)) {
      setIsOpen(false);
    }
  };

  createEffect(() => {
    if (isOpen()) {
      props.onOpen && props.onOpen();
    } else {
      props.onClose && props.onClose();
    }
  });

  createEffect(() => {
    if (isOpen()) {
      document.addEventListener("click", handleClickOutside, true);
    } else {
      document.removeEventListener("click", handleClickOutside, true);
    }

    onCleanup(() => document.removeEventListener("click", handleClickOutside, true));
  });

  return (
    <DropdownWrapper
      ref={dropdownRef}
      data-is-open={isOpen()}
      class={props.class}
      id={props.id}
    >
      <SelectedValueDisplay
        class={classNames({
          [SelectedValuePlaceholder]: !isValueSelected() && !!props.placeholder,
          [SelectedValueDisplayChangeable]: canSelectionChange(),
        })}
        type="button"
        onClick={handleSelectedValueDisplayClick}
        aria-haspopup="listbox"
        aria-expanded={isOpen()}
      >
        {displayLabel()}
        <Show when={canSelectionChange()}>
          <Arrow />
        </Show>
      </SelectedValueDisplay>

      <OptionsList role="listbox" aria-hidden={!isOpen()}>
        <Show when={props.optionListHeader}>
          <OptionListHeader>
            {props.optionListHeader}
          </OptionListHeader>
        </Show>
        <For each={props.options}>{option => (
          <OptionItem
            role="option"
            class={classNames({
              [SelectedOptionClass]: option.value === currentValue(),
              [DisabledOptionClass]: option.disabled,
            })}
            onClick={() => handleOptionClick(option)}
            aria-selected={option.value === currentValue()}
          >
            {option.label}
          </OptionItem>
        )}</For>
      </OptionsList>
    </DropdownWrapper>
  );
};

const DropdownWrapper = styled.div`
  position: relative;
  display: inline-block;
  min-width: 100px;
  color: inherit;
`;

const SelectedValueDisplay = styled.button`
  display: flex;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;

  background-color: transparent;
  font: inherit;
  color: inherit;
  text-align: left;

  align-items: center;
  justify-content: space-between;

  outline: none;
  transition: border-color 0.2s ease;

  &:focus-visible {
    border-color: var(--color-accA);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const SelectedValueDisplayChangeable = css`
  cursor: pointer;
`;

const SelectedValuePlaceholder = css`
  color: var(--color-fg-3);
`;

const Arrow = styled.span`
  margin-left: 10px;
  margin-top: -3px;
  border-style: solid;
  border-color: currentColor;
  border-width: 0 2px 2px 0;
  display: inline-block;
  padding: 2px;
  transform: rotate(45deg);
  transition: transform var(--fade-transition-duration-default) ease;
  flex-shrink: 0;

  /* Rotate arrow when the dropdown is open */
  /*${DropdownWrapper}[data-is-open="true"] & {
    transform: rotate(-135deg);
    margin-top: 1px;
  }*/
`;

const OptionsList = styled.ul`
  position: absolute;
  top: calc(100% + 2px);
  margin: 0;
  padding: 0;
  left: 50%;
  transform: translateX(-50%) translateY(-5px); /* Start slightly up */
  border: 1px solid var(--color-bg2-hl);
  border-radius: var(--border-radius-default);
  max-height: 200px; /* Limit height and enable scrolling */

  background-color: var(--color-bg2);
  color: var(--color-fg);
  box-shadow: var(--shadow-panel);
  list-style: none;

  overflow-y: auto; /* Enable vertical scrolling if content exceeds max-height */
  z-index: 100;

  opacity: 0;
  visibility: hidden;
  pointer-events: none; /* Prevent interaction when hidden */
  transition: opacity var(--fade-transition-duration-default) ease-out,
              transform var(--fade-transition-duration-default) ease-out,
              visibility 0s var(--fade-transition-duration-default);

  ${DropdownWrapper}[data-is-open="true"] & {
    opacity: 1;
    transform: translateX(-50%) translateY(0); /* Move to final position */
    visibility: visible;
    pointer-events: auto;
    transition: opacity var(--fade-transition-duration-default) ease-out,
                transform var(--fade-transition-duration-default) ease-out,
                visibility 0s 0s;
  }
`;

const OptionListHeader = styled.div`
  font-size: 0.85rem;
  padding: 0.5rem;
`;

const OptionItem = styled.li`
  padding: 10px;
  margin: 0;

  cursor: pointer;
  transition: background-color var(--fade-transition-duration-default) ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    background-color: var(--color-bg3);
  }
`;

const SelectedOptionClass = css`
  background-color: var(--color-bg2-hl);
`;

const DisabledOptionClass = css`
  color: var(--color-fg5);
  cursor: not-allowed;
  background-color: transparent !important;
  pointer-events: none;
`
