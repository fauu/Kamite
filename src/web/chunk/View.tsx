import { Show, type Ref, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { Spinner } from "~/common";
import { themeLayoutFlipped } from "~/theme";

import type { ChunksState } from "./ChunksState";
import { CurrentTranslation } from "./CurrentTranslation";
import { ChunkInput } from "./Input";
import { ChunkLabelWrapper } from "./LabelWrapper";

interface ChunkViewProps {
  chunksState: ChunksState,
  onInput: (newText: string) => void,
  inputRef: Ref<HTMLTextAreaElement>,
  labelAndTranslationRef: Ref<HTMLDivElement>,
}

export const ChunkView: VoidComponent<ChunkViewProps> = (props) => {
  const handleRootDblClick = () => !props.chunksState.waiting() && props.chunksState.startEditing();

  const handleInputCtrlEnter = () => props.chunksState.finishEditing();

  return <Root onDblClick={handleRootDblClick}>
    <Main class={props.chunksState.waiting() ? WaitingClass : ""}>
      <Show
        when={props.chunksState.editing()}
        fallback={
          <ChunkLabelAndTranslation ref={props.labelAndTranslationRef}>
            <ChunkLabelWrapper chunksState={props.chunksState} />
            <Show when={props.chunksState.translationWithContext()} keyed>{translations =>
              <CurrentTranslation translations={translations}/>
            }</Show>
          </ChunkLabelAndTranslation>
        }
      >
        <ChunkInput
          text={props.chunksState.editText()}
          onInput={props.onInput}
          onCtrlEnter={handleInputCtrlEnter}
          ref={props.inputRef}
        />
      </Show>
    </Main>
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
  justify-content: ${p => !themeLayoutFlipped(p.theme) ? "initial" : "flex-end"};
`;

const Main = styled.div`
  height: 100%;
`;

const WaitingClass = css`
  pointer-events: none;
  opacity: 0.3;
  transition: opacity 0.3s;
`;

const SpinnerContainer = styled.div`
  position: relative;
  left: 0.7rem;
  bottom: 0.58rem;
`;

const ChunkLabelAndTranslation = styled.div`
  overflow-y: auto;
  overflow-x: hidden;
`;
