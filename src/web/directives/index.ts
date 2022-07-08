export * from "./holdClickEvent";
export * from "./horizontalWheelScroll";
export * from "./hoverEvent";
export * from "./onMount";
export * from "./scrollToBottomOnChildListChange";
export * from "./tooltipAnchor";

import type { HoldClickEventParams } from "./holdClickEvent";
import type { HoverEventParams } from "./hoverEvent";
import type { OnMountParams } from "./onMount";
import type { ScrollToBottomOnChildListChangeParams } from "./scrollToBottomOnChildListChange";
import type { TooltipAnchorParams } from "./tooltipAnchor";

declare module "solid-js" {
  namespace JSX {
    interface Directives {
      holdClickEvent: HoldClickEventParams,
      horizontalWheelScroll: boolean,
      hoverEvent: HoverEventParams,
      onMount: OnMountParams,
      scrollToBottomOnChildListChange: ScrollToBottomOnChildListChangeParams,
      tooltipAnchor: TooltipAnchorParams,
    }
  }
}
