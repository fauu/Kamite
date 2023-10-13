import type { Middleware, Placement } from "@floating-ui/core";

export const overridePlacement: Middleware = {
  name: "overridePlacement",
  fn(state) {
    const placement = (state.elements.reference as HTMLElement).dataset
      .tooltipPlacementOverride as Placement;
    if (placement) {
      return { reset: { placement } };
    }
    return {};
  },
};
