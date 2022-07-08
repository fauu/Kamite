import { Show, type JSX, type VoidComponent } from "solid-js";

interface CharMaybeNewlineProps {
  value: string,
  newlineAs: JSX.Element,
}

export const CharMaybeNewline: VoidComponent<CharMaybeNewlineProps> = (props) =>
  <Show when={props.value !== "\n"} fallback={props.newlineAs}>{props.value}</Show>;
