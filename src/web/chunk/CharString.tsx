import { For, type ParentComponent } from "solid-js";

import { ChunkChar } from "./Char";

interface ChunkCharStringProps {
  value: string,
  startIdx?: number,
  selected: boolean[],
  highlighted: boolean[],
}

export const ChunkCharString: ParentComponent<ChunkCharStringProps> = (props) => 
  <For each={[...props.value]}>{(ch, i) =>
    <ChunkChar
      value={ch}
      selected={props.selected[i()]}
      highlighted={props.highlighted[i()]}
      idx={(props.startIdx || 0) + i()}
    />
  }</For>;
