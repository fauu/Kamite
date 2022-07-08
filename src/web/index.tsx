/* @refresh reload */
import { render } from "solid-js/web";

import { App } from "./App";
import { GlobalTooltipProvider } from "./GlobalTooltip";

render(
  () => <GlobalTooltipProvider><App /></GlobalTooltipProvider>,
  document.getElementById("app")!
);
