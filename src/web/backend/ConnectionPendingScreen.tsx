import { createEffect, createSignal, onCleanup, onMount, Show, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

const MAX_DOT_COUNT = 3;

interface BackendConnectionPendingScreenProps {
  contentDisplayDelayMS: number,
  dotAnimationStepMS: number,
}

export const BackendConnectionPendingScreen: VoidComponent<BackendConnectionPendingScreenProps> =
  (props) => {
    const [displayContent, setDisplayConent] = createSignal(false);
    const [dotCount, setDotCount] = createSignal(MAX_DOT_COUNT);

    onMount(() => {
      const displayContentTimeout = setTimeout(
        () => setDisplayConent(true),
        props.contentDisplayDelayMS
      );
      onCleanup(() => clearInterval(displayContentTimeout));
    });

    createEffect(() => {
      if (!displayContent()) {
        return;
      }

      const dotAnimationInterval = setInterval(() => {
        setDotCount(curr => {
          const next = curr + 1;
          return next > MAX_DOT_COUNT ? 0 : next;
        });
      }, props.dotAnimationStepMS);

      onCleanup(() => clearInterval(dotAnimationInterval));
    });

    return <Root id="backend-connection-pending-screen">
      <Show when={displayContent()}>
        <>
          <AppName>Kamite</AppName>
          <div>
            Waiting for backend connection<Dots>{Array(dotCount()).fill(".")}</Dots>
          </div>
        </>
      </Show>
    </Root>;
  };

const Root = styled.div`
  background: var(--color-bg);
  z-index: 1000;
  position: absolute;
  width: 100vw;
  height: 100vh;
  padding-top: 8rem;
  font-size: 1.1rem;
  font-weight: 500;
  display: flex;
  flex-direction: column;
  align-items: center;
`;

const AppName = styled.div`
  font-size: 3.75rem;
  letter-spacing: -0.2rem;
  font-weight: 500;
  margin-bottom: 4rem;
  text-shadow: 0 0 3px var(--color-fg4);
`;

const Dots = styled.span`
  display: inline-block;
  max-width: 0;
`;
