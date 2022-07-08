import { type VoidComponent } from "solid-js";

import { BlinkingClass, HalfBlinkingClass } from "~/common";

import { StatusPanelIndicator } from "../Indicator";
import type { SessionTimerState } from "./State";

interface SessionTimerProps {
  state: SessionTimerState,
  onClick: () => void,
  onHoldClick: () => void,
}

export const SessionTimer: VoidComponent<SessionTimerProps> = (props) =>
  <StatusPanelIndicator
    tooltipHeader="Session timer"
    tooltipBody={<>
      Click to {!props.state.running() && "un"}pause<br/>
      Click and hold to reset<br/>
    </>}
    onClick={props.onClick}
    onHoldClick={props.onHoldClick}
    id="session-timer"
  >
    <div classList={{ [BlinkingClass]: !props.state.running() }}>
      <span>{props.state.time().h}</span>
      <span classList={{ [HalfBlinkingClass]: props.state.running() }}>:</span>
      <span>{`${props.state.time().m}`.padStart(2, "0")}</span>
    </div>
  </StatusPanelIndicator>;
