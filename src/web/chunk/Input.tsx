import { createEffect, type Accessor, type Ref, type VoidComponent } from "solid-js";
import { css } from "solid-styled-components";

import { ChunkTextClass } from "./TextClass";

import { onMount } from "~/directives";
const [_] = [onMount];

interface ChunkInputProps {
  text: Accessor<string>,
  onInput: (newText: string) => void,
  ref: Ref<HTMLTextAreaElement>,
}

export const ChunkInput: VoidComponent<ChunkInputProps> = (props) => {
  let textareaEl: HTMLTextAreaElement;

  const handleInput = ({ target }: InputEvent) =>
    props.onInput((target as HTMLTextAreaElement).value);

  const handleTextareaMount = {
    run: (el: HTMLElement) => {
      textareaEl = el as HTMLTextAreaElement;
      textareaEl.focus();
      textareaEl.selectionStart = textareaEl.value.length;
    },
  };

  createEffect(() => {
    // NOTE: Keep access outside the if
    const text = props.text();
    if (textareaEl) {
      textareaEl.value = text;
    }
  });

  return <textarea
      lang="ja"
      class={RootClass}
      classList={{ [ChunkTextClass]: true }}
      onInput={handleInput}
      use:onMount={handleTextareaMount}
      ref={props.ref}
      id="chunk-input"
    >
      {props.text()}
    </textarea>;
};

const RootClass = css`
  color: inherit;
  background: var(--color-bg2);
  border: none;
  font-family: inherit;
  flex: 1;
  margin: 0;
  padding-top: var(--text-margin-top);
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
