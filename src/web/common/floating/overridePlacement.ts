import { Middleware } from "@floating-ui/core";

export const overridePlacement: Middleware = {
  name: "overridePlacement",
  async fn(state) {
    const placement = state.elements.reference.dataset.tooltipPlacementOverride;
    if (placement) {
      return { reset: { placement } }
    }
    return {};
  },
};

