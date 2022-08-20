import { createEffect, createSignal, onCleanup, Show, type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

import { ChromeClassName } from "~/globalStyles";

import type { BackendConnectionState } from "./Backend";

const MAX_DOT_COUNT = 3;

interface BackendNotConnectedScreenProps {
  connectionState: BackendConnectionState,
  contentDisplayDelayMS: number,
  dotAnimationStepMS: number,
  onReconnectClick: () => void,
}

export const BackendNotConnectedScreen: VoidComponent<BackendNotConnectedScreenProps> =
  (props) => {
    const [displayContent, setDisplayConent] = createSignal(false);
    const [dotCount, setDotCount] = createSignal(MAX_DOT_COUNT);

    createEffect(() => {
      if (displayContent()) {
        const dotAnimationInterval = setInterval(() => {
          setDotCount(curr => {
            const next = curr + 1;
            return next > MAX_DOT_COUNT ? 0 : next;
          });
        }, props.dotAnimationStepMS);
        onCleanup(() => clearInterval(dotAnimationInterval));
      } else {
        const displayContentTimeout = setTimeout(
          () => setDisplayConent(true),
          props.contentDisplayDelayMS
        );
        onCleanup(() => clearTimeout(displayContentTimeout));
      }
    });

    const handleReconnectClick = () => {
      setDisplayConent(false);
      props.onReconnectClick();
    };

    return <Root id="backend-connection-pending-screen">
      <Show when={displayContent()}>
        <>
          <AppName>Kamite</AppName>
          <Show
            when={props.connectionState === "disconnected-wont-reconnect"}
            fallback={
              <div>
                Waiting for backend connection<Dots>{Array(dotCount()).fill(".")}</Dots>
              </div>
            }
          >
          <ReconnectButton onClick={handleReconnectClick}>Reconnect</ReconnectButton>
          </Show>
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

// TODO: (DRY) PaletteButton
const ReconnectButton = styled.div`
  background-color: var(--color-bg2);
  min-width: var(--palette-button-min-size);
  height: var(--palette-button-min-size);
  line-height: var(--palette-button-min-size);
  border-radius: var(--border-radius-default);
  padding: 0 0.8rem;
  cursor: pointer;

  box-shadow: inset 0px 0px 1px var(--color-bg3-hl);
  .${ChromeClassName} & {
    box-shadow: inset 0px 0px 2px var(--color-bg3-hl);
  }

  &:hover {
    background-color: var(--color-bg3);
  }
`;
