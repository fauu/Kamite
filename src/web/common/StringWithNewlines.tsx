import { Index, type JSX, type VoidComponent } from "solid-js";

import { CharMaybeNewline } from "./CharMaybeNewline";

interface StringWithNewlinesProps {
  value: string,
  newlineAs: JSX.Element,
}

export const StringWithNewlines: VoidComponent<StringWithNewlinesProps> = (props) =>
  <Index each={[...props.value]}>{(c, _) =>
    <CharMaybeNewline value={c()} newlineAs={props.newlineAs} />
  }</Index>;
