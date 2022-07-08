import { type VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

interface ConfigKeyProps {
  value: string,
}

export const ConfigKey: VoidComponent<ConfigKeyProps> = (props) =>
  <Root>{props.value}</Root>;

const Root = styled.span`
  font-family: var(--font-stack-mono);
  letter-spacing: -0.03rem;
  font-weight: 500;
  font-size: 0.8rem;
  color: var(--color-fg5);
`;
