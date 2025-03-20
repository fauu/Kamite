import { type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

interface ChunkPickerVariantPickButtonProps {
  variantBeingSelectedIn: boolean,
  onClick: () => void,
}

export const ChunkPickerVariantPickButton
  : VoidComponent<ChunkPickerVariantPickButtonProps> = (props) =>
    <div
      role="button"
      class={ChunkPickerVariantPickButtonClass}
      onClick={props.onClick}
    >
      <span
        class={LabelClass}
        classList={{
          [VariantBeingSelectedInClass]: props.variantBeingSelectedIn
        }}
      >
        Pick
      </span>
    </div>;

export const ChunkPickerVariantPickButtonClass = css`
  background: var(--color-bg3);
  border-radius: var(--border-radius-default);
  border: 1px solid var(--color-bg3-hl);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  padding: 0.35rem 0.6rem;
  margin-left: 0.5rem;
  align-items: center;
  position: absolute;
  right: 0rem;
  bottom: 0.32rem;

  &:hover {
    background-color: var(--color-bg4);
    border-color: var(--color-med);
  }
`;

const VariantBeingSelectedInClass = "selected-in";

const LabelClass = css`
  padding: 0 0.2rem;
  border-radius: var(--border-radius-default);

  &.${VariantBeingSelectedInClass} {
    background: linear-gradient(0, var(--color-accB2) 0%, transparent 100%);
  }
`;
