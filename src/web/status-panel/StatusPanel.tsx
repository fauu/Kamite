import { createSignal, type Accessor, type ParentComponent, type Ref } from "solid-js";
import { css, useTheme, type DefaultTheme } from "solid-styled-components";

import { concealUnlessHovered } from "~/directives";
const [_] = [concealUnlessHovered];

import { themeLayoutFlipped } from "~/theme";

interface StatusPanelProps {
  fade: boolean,
  concealUnlessHovered: Accessor<boolean>,
  ref: Ref<HTMLDivElement>,
}

export const StatusPanel: ParentComponent<StatusPanelProps> = (props) => {
  const theme = useTheme();

  const [overrideFade, setOverrideFade] = createSignal(false);

  const handleMouseOver = () => setOverrideFade(true);
  const handleMouseOut = () => setOverrideFade(false);

  const shouldFade = () => props.fade && !overrideFade();

  return <div
      // NOTE: Must be set through `class`, not `classList`, or else it isn't applied properly on
      //       flipping the layout (isn't faded despite `props.fade` being true the whole time)
      class={[RootClass({ theme }), shouldFade() ? FadedClass : ""].join(" ")}
      onMouseOver={handleMouseOver}
      onMouseOut={handleMouseOut}
      ref={props.ref}
      use:concealUnlessHovered={{ enabled: props.concealUnlessHovered }}
      id="status-panel"
    >
      {props.children}
    </div>;
};

const RootClass = (p: { theme?: DefaultTheme }) => css`
  display: flex;
  padding: 0.5rem;
  border-radius: var(--border-radius-default);
  background: var(--color-bg);
  position: absolute;
  right: 0;
  ${!themeLayoutFlipped(p.theme) ? "bottom: 0" : "top: 0"};
  z-index: 15;
  transition: opacity var(--fade-transition-duration-default) ease-in;
`;

const FadedClass = css`
  opacity: 0.3;
  background: none;
`;
