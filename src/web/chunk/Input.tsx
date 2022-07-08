import type { Ref, VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { ChunkTextClass } from "./TextClass";

import { onMount } from "~/directives";
const [_] = [onMount];

interface ChunkInputProps {
  text: string,
  onInput: (newText: string) => void,
  onCtrlEnter: (el: HTMLElement) => any,
  ref: Ref<HTMLTextAreaElement>,
}

export const ChunkInput: VoidComponent<ChunkInputProps> = (props) => {
  const handleInput = ({ target }: InputEvent) =>
    props.onInput((target as HTMLTextAreaElement).value);

  const handleTextareaMount = {
    run: (el: HTMLElement) => {
      el.focus();

      const taEl = el as HTMLTextAreaElement;
      taEl.selectionStart = taEl.value.length;

      el.addEventListener("keydown", handleTextareaKeydown);
    },
    cleanup: (el: HTMLElement) => {
      el.removeEventListener("keydown", handleTextareaKeydown);
    },
  };

  const handleTextareaKeydown = (event: KeyboardEvent) => {
    if (event.code === "Enter" && event.ctrlKey) {
      props.onCtrlEnter(event.target as HTMLElement);
      event.preventDefault();
    }
  };

  return <textarea
      lang="ja"
      class={RootClass}
      classList={{ [ChunkTextClass]: true }}
      onInput={handleInput}
      use:onMount={handleTextareaMount}
      ref={props.ref}
      id="chunk-input"
    >
      {props.text}
    </textarea>;
};

const RootClass = css`
  color: inherit;
  background: var(--color-bg2);
  border: none;
  font-family: inherit;
  width: 100%;
  height: 100%;
  margin: 0;
  resize: none;
  user-select: initial;
  caret-color: var(--color-accB);
  box-shadow: inset 0px 1px 3px var(--color-bg);
  z-index: 5;

  &:focus,
  &:active {
    outline: none;
  }
`;
