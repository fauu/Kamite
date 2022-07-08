import { createSignal, Show, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import type { CurrentTranslationWithContext } from "./ChunksState";

import { hoverEvent } from "~/directives";
const [_] = [hoverEvent];

interface CurrentTranslationProps {
  translations: CurrentTranslationWithContext,
}

export const CurrentTranslation: VoidComponent<CurrentTranslationProps> = (props) => {
  const [concealed, setConcealed] = createSignal(true);

  return <Root>
    <div
      classList={{
        [ConcealedClass]: concealed(),
      }}
      use:hoverEvent={{
        enterDelayMS: 250,
        enterCb: () => setConcealed(false),
        leaveCb: () => setConcealed(true),
      }}
      id="chunk-translation"
    >
      <Show when={props.translations.previous}>{text =>
        <PreviousText class={ChunkCurrentTranslationSelectionParentClass}>
          {text}
        </PreviousText>
      }</Show>
      <Show when={props.translations.current}>{text =>
        <Text class={ChunkCurrentTranslationSelectionParentClass}>
          {text}
        </Text>
      }</Show>
    </div>
  </Root>;
};

export const ChunkCurrentTranslationSelectionParentClass = "chunk-translation-selection-parent";

const Root = styled.div`
  margin: 0.8rem 0.4rem;
  line-height: 1.65rem;
  letter-spacing: -0.01rem;
`;

const Text = styled.div`
  font-size: 1.28rem;
  user-select: text;
`;

const PreviousText = styled.div`
  font-size: 1.03rem;
  line-height: 1.35rem;
  color: var(--color-fg4);
  user-select: initial;
  user-select: text;
`;

const ConcealedClass = css`
  filter: blur(7px);
  text-shadow: 0 0 3px var(--color-fg);
`;
