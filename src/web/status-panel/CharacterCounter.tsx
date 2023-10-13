import { Show, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import type { CharacterCounter as BackendCharacterCounter } from "~/backend";
import { BlinkingClass } from "~/common";

import { StatusPanelIndicator } from "./Indicator";
import { approxPaceCPH } from "./pace";
import type { SessionTimerState } from "./session-timer/State";

interface CharacterCounterProps {
  state: BackendCharacterCounter,
  timerState: SessionTimerState,
  onClick: () => void,
  onHoldClick: () => void,
}

export const CharacterCounter: VoidComponent<CharacterCounterProps> = (props) => {
  const pace = () => approxPaceCPH(props.state.count, props.timerState.time());

  return <StatusPanelIndicator
    tooltipHeader="Character counter"
    tooltipBody={<>
      Click to {props.state.frozen && "un"}freeze<br />
      Long click to reset<br />
    </>}
    onClick={props.onClick}
    onHoldClick={props.onHoldClick}
    id="character-counter"
  >
    <div classList={{ [BlinkingClass]: props.state.frozen }}>
      <span id="character-count">{props.state.count}</span>
      <Show when={pace()}>{pace =>
        <Pace id="reading-pace">~{pace()}<PaceUnit>/h</PaceUnit></Pace>
      }</Show>
    </div>
  </StatusPanelIndicator>;
};

const Pace = styled.span`
  color: var(--color-med2);
  font-weight: normal;
  margin-left: 0.3rem;
`;

const PaceUnit = styled.span`
  font-size: 0.8rem;
`;
