import { glob } from "goober";
import { css, keyframes } from "solid-styled-components";

export const LAYOUT_BREAKPOINT_SMALL = "@media (max-width: 600px)";

glob`
  :root {
    --color-bgm2: #201D1B;
    --color-bgm1: #252320;
    --color-bg: #383532;
    --color-bg-hl: #403D3A;
    --color-bg2: #484542;
    --color-bg2-hl: #504D4A;
    --color-bg3: #585552;
    --color-bg3-hl: #605D5A;
    --color-bg4: #686562;
    --color-med: #787572;
    --color-med2: #888583;
    --color-med3: #989593;
    --color-fg: #ffffff;
    --color-fg2: #f8f5f2;
    --color-fg3: #e8e5e2;
    --color-fg4: #c8c5c2;
    --color-fg5: #a5a2a0;
    --color-accA: #fffd96;
    --color-accA2: #dbd984;
    --color-accB: #f5c4e4;
    --color-accB2: #b0769c;
    --color-accB2-hl: #ba80a6;
    --color-accC: #c8c5a3;
    --color-success: #9bde37;
    --color-warning: #c29f48;
    --color-warning-hl: #cca952;
    --color-error: #de382c;
    --color-error2: #c25048;
    --color-error2-hl: #cc5a52;

    --base-font-size: 16px;
    --font-stack: Roboto, 'Noto Sans JP', 'Noto Sans CJK JP', 'Hiragino Sans',
                  'Hiragino Kaku Gothic Pro', '游ゴシック' , '游ゴシック体' , YuGothic ,
                  'Yu Gothic', 'ＭＳ ゴシック' , 'MS Gothic', 'Segoe UI', Helvetica, Ubuntu,
                  Cantarell, Arial, sans-serif;
    --font-stack-mono: 'Roboto Mono', Menlo, Monaco, 'Noto Sans Mono', Courier, monospace;
    --border-radius-default: 2px;
    --chunk-font-size: 2.1rem;
    --chunk-line-height: 2.83rem;
    --chunk-letter-spacing: -0.07rem;
    --shadow-panel: rgba(0, 0, 0, 0.07) 0px 1px 2px, rgba(0, 0, 0, 0.07) 0px 2px 4px, rgba(0, 0, 0, 0.07) 0px 4px 8px, rgba(0, 0, 0, 0.07) 0px 8px 16px, rgba(0, 0, 0, 0.07) 0px 16px 32px, rgba(0, 0, 0, 0.07) 0px 32px 64px;
    --shadow-faint-glow: 0 0 5px 3px var(--color-bg3);
    --palette-button-min-size: 46px;
    --palette-button-padding-fix: 1px; /* TODO: Try to do without this */
    --notebook-tab-size: 52px;
    --form-control-height: 32px;
  }

  * {
    box-sizing: border-box;
    user-select: none;
  }

  ::selection {
    color: var(--color-fg);
    background: var(--color-accB2);
  }

  html {
    overflow: hidden;
    font-size: var(--base-font-size);
    font-family: var(--font-stack);
  }

  body {
    margin: 0;
    padding: 0;
    min-height: 100vh;
  }

  a {
    color: var(--color-accB);
    text-decoration: none;
  }
  a:hover {
    text-decoration: underline;
  }

  h1, h2, h3, h4, h5, h6 {
    margin-top: 0;
  }

  h2 {
    margin-bottom: 1rem;
  }

  .toastify {
    font-weight: 500;
    padding: 0.5rem 0.9rem;
    border: 1px solid;
    border-radius: var(--border-radius-default);
    box-shadow: var(--shadow-panel);

    &.info {
      background: var(--color-med2);
      border-color: var(--color-med3);
    }

    &.warning {
      background: var(--color-warning);
      border-color: var(--color-warning-hl);
    }

    &.error {
      background: var(--color-error2);
      border-color: var(--color-error2-hl);
    }
  }
`;

glob`
  @font-face {
    font-family: 'Roboto';
    font-style: normal;
    font-weight: 400;
    src: local(''),
          url('fonts/roboto-v30-latin_greek_cyrillic-regular.woff2') format('woff2');
  }
`;
glob`
  @font-face {
    font-family: 'Roboto';
    font-style: normal;
    font-weight: 500;
    src: local(''),
          url('fonts/roboto-v30-latin_greek_cyrillic-500.woff2') format('woff2');
  }
`;
glob`
  @font-face {
    font-family: 'Roboto';
    font-style: normal;
    font-weight: 700;
    src: local(''),
          url('fonts/roboto-v30-latin_greek_cyrillic-700.woff2') format('woff2');
  }
`;
glob`
  @font-face {
    font-family: 'Roboto Mono';
    font-style: normal;
    font-weight: 400;
    src: local(''),
          url('fonts/roboto-mono-v22-latin-regular.woff2') format('woff2');
  }
`;
glob`
  @font-face {
    font-family: 'Noto Sans JP';
    font-style: normal;
    font-weight: 400;
    src: local(''),
         url('fonts/noto-sans-jp-v42-latin_japanese-regular.woff2') format('woff2');
  }
`;

export const ChromeClass = "chrome";

export const PaletteButtonDisabledClass = css`
  opacity: 0.5;
`;

export const PaletteButtonClass = css`
  background-color: var(--color-bg2);
  min-width: var(--palette-button-min-size);
  height: var(--palette-button-min-size);
  line-height: var(--palette-button-min-size);
  border-radius: var(--border-radius-default);
  background-repeat: no-repeat;
  background-size: 32px;
  background-position: center;
  padding: var(--palette-button-padding-fix) 0.8rem 0 0.8rem;

  &:not(.${PaletteButtonDisabledClass}) {
    cursor: pointer;
  }

  /* TODO: (DRY) notebook/Tab.tsx, notebook/chunk-history/ActionPalette.tsx. Some others share
   *             the same pattern */
  box-shadow: inset 0px 0px 1px var(--color-bg3-hl);
  .${ChromeClass} & {
    box-shadow: inset 0px 0px 2px var(--color-bg3-hl);
  }

  &:first-child {
    border-radius: var(--border-radius-default) 0 0 var(--border-radius-default);
  }

  &:last-child {
    border-radius: 0 var(--border-radius-default) var(--border-radius-default) 0;
  }

  &:not(:last-child) {
    border-right: 1px solid var(--color-bg);
  }

  &:not(.${PaletteButtonDisabledClass}):hover {
    background-color: var(--color-bg3);
  }
`;

const bgFlashKeyframes = keyframes`
  0% {
    background-color: initial;
  }
  50% {
    background-color: var(--color-accA2);
  }
  100% {
    background-color: initial;
  }
`;

export const BG_FLASH_DURATION_MS = 300;

export const BgFlashingClass = css`
  animation: ${bgFlashKeyframes} ${BG_FLASH_DURATION_MS}ms linear 1;
`;
