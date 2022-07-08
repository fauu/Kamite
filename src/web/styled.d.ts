import "solid-styled-components";

import type { ThemeLayout } from "./theme";

declare module "solid-styled-components" {
  interface DefaultTheme {
    layout: ThemeLayout,
  }
}
