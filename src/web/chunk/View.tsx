import { Show, type Ref, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { Spinner } from "~/common";
import { themeLayoutFlipped } from "~/theme";

import type { ChunksState } from "./ChunksState";
import { CurrentTranslation } from "./CurrentTranslation";
import { ChunkInput } from "./Input";
import { ChunkLabel } from "./Label";

interface ChunkViewProps {
  chunksState: ChunksState,
  onInput: (newText: string) => void,
  labelRef: Ref<HTMLSpanElement>,
  inputRef: Ref<HTMLTextAreaElement>,
  labelAndTranslationRef: Ref<HTMLDivElement>,
}

export const ChunkView: VoidComponent<ChunkViewProps> = (props) => {
  const handleRootDblClick = () => props.chunksState.startEditing();

  const handleInputCtrlEnter = () => props.chunksState.finishEditing();

  return <Root onDblClick={handleRootDblClick}>
    <Show
      when={!props.chunksState.waiting()}
      fallback={
        <SpinnerContainer>
          <Spinner
            size="28px"
            fgColor="var(--color-fg)"
            bgColor="var(--color-bg)"
            id="chunk-spinner"
          />
        </SpinnerContainer>
      }
    >
      <Show
        when={props.chunksState.editing()}
        fallback={
          <ChunkLabelAndTranslation ref={props.labelAndTranslationRef}>
            <Show when={!props.chunksState.current().text.isEmpty}>
              <ChunkLabel
                text={props.chunksState.current().text}
                flashState={props.chunksState.currentFlashState()}
                selection={props.chunksState.textSelection.get()}
                highlight={props.chunksState.textHighlight()}
                ref={props.labelRef}
              />
            </Show>
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

const SpinnerContainer = styled.div`
  padding: 0.75rem;
`;

const ChunkLabelAndTranslation = styled.div`
  overflow-y: scroll;
  overflow-x: hidden;
`;
