import { styled } from "solid-styled-components";

interface DefaultIconProps {
  iconName: string,
  sizePx: number,
}

export const DefaultIcon = styled.div<DefaultIconProps>`
  --size: ${p => p.sizePx}px;
  background: var(--color-fg);
  width: var(--size);
  height: 100%;
  mask: url('icons/${p => p.iconName}.svg') no-repeat center center;
  mask-size: var(--size);
  margin: 0 auto;
`;

