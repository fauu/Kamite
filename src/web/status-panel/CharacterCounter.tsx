import { type VoidComponent } from "solid-js";

import type { CharacterCounter as BackendCharacterCounter } from "~/backend";
import { BlinkingClass } from "~/common";

import { StatusPanelIndicator } from "./Indicator";

interface CharacterCounterProps {
  state: BackendCharacterCounter,
  onClick: () => void,
  onHoldClick: () => void,
}

export const CharacterCounter: VoidComponent<CharacterCounterProps> = (props) =>
  <StatusPanelIndicator
    tooltipHeader="Character counter"
    tooltipBody={<>
      Click to {props.state.frozen && "un"}freeze<br/>
      Long click to reset<br/>
    </>}
    onClick={props.onClick}
    onHoldClick={props.onHoldClick}
    id="character-counter"
  >
    <div classList={{ [BlinkingClass]: props.state.frozen }}>
      {props.state.count}
    </div>
  </StatusPanelIndicator>;
