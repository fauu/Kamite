import { For, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { scrollToBottomOnChildListChange } from "~/directives";
const [_] = [scrollToBottomOnChildListChange];

import type { DebugState } from "./State";

interface DebugProps {
  state: DebugState,
}

export const Debug: VoidComponent<DebugProps> = (props) =>
  <div
    class={RootClass}
    // Needs delay because a new image doesn't load immediately
    use:scrollToBottomOnChildListChange={{ scrollParent: true, delay: true }}
    id="debug"
  >
    <For each={props.state.images}>{img =>
      <Image src={`data:image/png;base64,${img}`} />
    }</For>
  </div>;

const RootClass = css`
  padding-bottom: 0.5rem;
`;

const Image = styled.img`
  margin: 0 0.5rem 0.5rem 0;
`;
