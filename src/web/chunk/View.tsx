import { Show, type Accessor, type Ref, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { Spinner } from "~/common";
import { themeLayoutFlipped } from "~/theme";

import type { ChunksState } from "./ChunksState";
import { CurrentTranslation } from "./CurrentTranslation";
import { ChunkInput } from "./Input";
import { ChunkLabelWrapper } from "./label";

interface ChunkViewProps {
  chunksState: ChunksState,
  onInput: (newText: string) => void,
  inputRef: Ref<HTMLTextAreaElement>,
  labelAndTranslationRef: Ref<HTMLDivElement>,
  labelSelectionAutoHighlight: Accessor<boolean>,
  movingMouseWhilePrimaryDown: Accessor<boolean>,
}

export const ChunkView: VoidComponent<ChunkViewProps> = (props) => {
  const handleRootDblClick = () => !props.chunksState.waiting() && props.chunksState.startEditing();

  const maybeWaitingClass = () => props.chunksState.waiting() ? WaitingClass : "";

  return <Root onDblClick={handleRootDblClick}>
    <Show
      when={props.chunksState.editing()}
      fallback={
        <ChunkLabelAndTranslation class={maybeWaitingClass()} ref={props.labelAndTranslationRef}>
          <ChunkLabelWrapper
            chunksState={props.chunksState}
            movingMouseWhilePrimaryDown={props.movingMouseWhilePrimaryDown}
            selectionAutoHighlight={props.labelSelectionAutoHighlight}
          />
          <Show when={props.chunksState.translationWithContext()} keyed>{translations =>
            <CurrentTranslation translations={translations}/>
          }</Show>
        </ChunkLabelAndTranslation>
      }
    >
      <ChunkInputWrapper class={maybeWaitingClass()}>
        <ChunkInput
          text={props.chunksState.editText}
          onInput={props.onInput}
          ref={props.inputRef}
        />
      </ChunkInputWrapper>
    </Show>
    <Show when={props.chunksState.waiting()}>
      <SpinnerContainer>
        <Spinner size="28px" color="var(--color-fg)" id="chunk-spinner" />
      </SpinnerContainer>
    </Show>
  </Root>;
};

const Root = styled.div`
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  justify-content: ${p => !themeLayoutFlipped(p.theme) ? "initial" : "flex-end"};
`;

const WaitingClass = css`
  pointer-events: none;
  opacity: 0.3;
  transition: opacity 0.2s;
`;

const SpinnerContainer = styled.div`
  position: absolute;
  left: 0.7rem;
  ${p => !themeLayoutFlipped(p.theme) ? "bottom" : "top"}: 0.62rem;
`;

const ChunkLabelAndTranslation = styled.div`
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: ${p => !themeLayoutFlipped(p.theme) ? "column" : "column-reverse"};
`;

const ChunkInputWrapper = styled.div`
  display: flex;
  height: 100%;
  z-index: 8; /* Above status panel, below notebook (to keep the shadow above) */
`;
