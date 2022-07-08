import { createContext, useContext, type ParentComponent } from "solid-js";

import { useTooltip, type Tooltip } from "~/common/floating";

const GlobalTooltipContext = createContext<Tooltip>();

export const GlobalTooltipProvider: ParentComponent = (props) => {
  const tooltip = useTooltip();

  return <GlobalTooltipContext.Provider value={tooltip}>
    {props.children}
  </GlobalTooltipContext.Provider>;
};

export const useGlobalTooltip = () => useContext(GlobalTooltipContext);
