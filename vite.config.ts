/* eslint-disable */
// @ts-nocheck

import path from "path";

import { defineConfig } from "vite";
import solidPlugin from "vite-plugin-solid";

export default defineConfig({
  root: "src/web",
  plugins: [
    solidPlugin()
  ],
  build: {
    outDir: "../../target/web",
    target: 'esnext',
    polyfillDynamicImport: false,
  },
  resolve: {
    alias: {
      "~": path.resolve(__dirname, "src/web"),
    }
  },
  server: {
    port: 3000
  }
});
