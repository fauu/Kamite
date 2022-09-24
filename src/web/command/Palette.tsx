import { For, Show, type Ref, type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { tooltipAnchor } from "~/directives";
const [_] = [tooltipAnchor];

import type { Command, PlayerStatus } from "~/backend";
import { DefaultIcon } from "~/common";
import { useGlobalTooltip } from "~/GlobalTooltip";
import { PaletteButtonClass, PaletteButtonDisabledClass } from "~/style";

import type { CommandPaletteCommand } from ".";

interface CommandPaletteProps {
  commands: CommandPaletteCommand[],
  mediaPlayerStatus: PlayerStatus,
  onCommand: (c: Command) => void,
  ref: Ref<HTMLDivElement>,
}

export const CommandPalette: VoidComponent<CommandPaletteProps> = (props) => {
  const tooltip = useGlobalTooltip()!;

  const hasIcon = (c: Command) => c.kind !== "misc_custom";

  const iconName = (c: Command) =>
    c.kind === "player_playpause"
    ? `player_${props.mediaPlayerStatus === "playing" ? "pause" : "play"}`
    : c.kind;

  const handleButtonClick = (c: Command) => props.onCommand(c);

  return <Root id="command-palette" ref={props.ref}>
    <For each={props.commands}>{pc =>
      <div
        role="button"
        class={ButtonClass}
        classList={{
          [PaletteButtonClass]: true,
          [PaletteButtonDisabledClass]: !pc.enabled
        }}
        data-kind={pc.command.kind}
        data-symbol={pc.symbol}
        use:tooltipAnchor={pc.description ? { tooltip, header: pc.description } : undefined}
        onClick={[handleButtonClick, pc.command]}
      >
        <Show when={hasIcon(pc.command)}>
          <Icon iconName={iconName(pc.command)} sizePx={32} />
        </Show>
      </div>
    }</For>
  </Root>;
};

const Root = styled.div`
  height: var(--palette-button-min-size);
  margin-right: 0.32rem;
  flex-shrink: 0;
  background: var(--color-bg2);
`;

const ButtonClass = css`
  display: inline-block;
  font-weight: 500;
  text-align: center;
  position: relative;

  &[data-symbol]:before {
    font-size: 0.7rem;
    content: attr(data-symbol);
    position: absolute;
    left: 0;
    right: 0;
    margin: 0 auto;
  }

  &[data-kind="ocr_region"]:before {
    bottom: 4px;
  }

  &[data-kind="misc_custom"]:before {
    font-size: 0.88rem;
  }
`;

const Icon = styled(DefaultIcon)`
  position: absolute;
  width: 100%;
  left: 0;
  top: 0;
`;
