import classNames from "classnames";
import { type Component, createSignal, createEffect, onCleanup, For, createMemo } from "solid-js";
import { css, styled } from "solid-styled-components";

interface DropdownOption {
	value: any;
	label: string;
	disabled?: boolean;
}

interface DropdownProps {
	options: DropdownOption[];
	placeholder?: string;
	value?: any;
	onChange?: (value: any) => void;
	onOpen?: () => void;
	onClose?: () => void;
	class?: string;
	id?: string;
}

// XXX: Review
export const Dropdown: Component<DropdownProps> = (props) => {
	const [isOpen, setIsOpen] = createSignal(false);

	let dropdownRef: HTMLDivElement | undefined;

	// Determine the current value (controlled or internal)
	// Note: In Solid, directly using props.value in effects/memos is often sufficient
	// as props are reactive. No separate internal signal strictly needed for controlled.
	const currentValue = createMemo(() => props.value);

	// Determine the displayed label
	const displayLabel = createMemo(() => {
		const currentVal = currentValue();
		const selectedOption = props.options?.find(opt => opt.value === currentVal);
		return selectedOption
			? selectedOption.label
			: props.placeholder || "Select...";
	});

	// Check if a value (other than placeholder) is actually selected
	const isValueSelected = createMemo(() => {
		// Check if props.value is defined and not null/undefined
		// Or if using internal state, check that state.
		return currentValue() !== undefined && currentValue() !== null;
	});

	const toggleDropdown = (event: MouseEvent) => {
		event.stopPropagation();
		setIsOpen(!isOpen());
    if (isOpen()) {
      props.onOpen && props.onOpen();
    } else {
      props.onClose && props.onClose();
    }
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

	// Add/remove the global click listener for closing the dropdown
	createEffect(() => {
		if (isOpen()) {
			document.addEventListener("click", handleClickOutside, true);
		} else {
			document.removeEventListener("click", handleClickOutside, true);
		}

		onCleanup(() => {
			document.removeEventListener("click", handleClickOutside, true);
		});
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
        })}
				type="button"
				onClick={toggleDropdown}
				aria-haspopup="listbox"
				aria-expanded={isOpen()}
			>
				{displayLabel()}
				<Arrow />
			</SelectedValueDisplay>

			<OptionsList role="listbox" aria-hidden={!isOpen()}>
				<For each={props.options}>{option => (
          <OptionItem
            role="option"
            class={classNames({
              [SelectedOptionClass]: option.value === currentValue,
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
  border: none;

  background-color: transparent;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;

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
  background-color: var(--color-bg3);
  font-weight: 500; /* Make selected item slightly bolder */
  /* Optionally hide hover effect when selected */
  /* &:hover { background-color: #e0e0e0; } */
`;

const DisabledOptionClass = css`
  color: #aaa; /* Dim text color */
  cursor: not-allowed;
  background-color: transparent !important; /* Ensure no background on hover/selection */
  /* pointer-events: none; */ /* Already handled by inline style in component logic */
  pointer-events: none;
`
