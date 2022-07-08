import { type JSX, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

interface CheckboxProps {
  checked: boolean,
  onChange?: JSX.EventHandler<HTMLInputElement, Event>,
}

export const Checkbox: VoidComponent<CheckboxProps> = (props) =>
  <Root type="checkbox" {...props} />;

const Root = styled.input`
  appearance: none;
  margin: 0;
  min-width: 1.2rem;
  min-height: 1.2rem;
  border: 2px solid var(--color-med2);
  border-radius: var(--border-radius-default);

  display: flex;
  align-items: center;
  justify-content: center;

  &:before {
    content: "";
    width: 0.7rem;
    height: 0.7rem;
    background-color: var(--color-accB);
    border-radius: var(--border-radius-default);
    transform-origin: bottom left;
    clip-path: polygon(15% 47%, 0 65%, 48% 100%, 100% 16%, 82% 0%, 43% 70%); /* Checkmark */
    transform: scale(0);
  }

  &:checked {
    &:before {
      transform: scale(1);
    }
  }
`;

