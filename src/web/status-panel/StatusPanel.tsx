import { createSignal, type ParentComponent, type Ref } from "solid-js";
import { css, styled } from "solid-styled-components";

import { themeLayoutFlipped } from "~/theme";

interface StatusPanelProps {
  fade: boolean,
  ref: Ref<HTMLDivElement>,
}
 
export const StatusPanel: ParentComponent<StatusPanelProps> = (props) => {
  const [overrideFade, setOverrideFade] = createSignal(false);

  const handleMouseOver = () => setOverrideFade(true);
  const handleMouseOut = () => setOverrideFade(false);

  return <Root
      classList={{
        [FadedClass]: props.fade && !overrideFade(),
      }}
      onMouseOver={handleMouseOver}
      onMouseOut={handleMouseOut}
      ref={props.ref}
      id="status-panel"
    >
      {props.children}
    </Root>;
};

const Root = styled.div`
  display: flex;
  padding: 0.5rem;
  border-radius: var(--border-radius-default);
  background: var(--color-bg);
  position: absolute;
  right: 0;
  ${p => !themeLayoutFlipped(p.theme) ? "bottom: 0" : "top: 0"};
`;

const FadedClass = css`
  opacity: 0.3;
  background: none;
`;
