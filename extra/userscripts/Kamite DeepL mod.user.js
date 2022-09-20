// ==UserScript==
// @name         Kamite DeepL mod
// @description  Improves DeepL user experience when embedded into Kamite
// @version      1.2.0
// @match        https://www.deepl.com/translator*
// @icon         https://www.google.com/s2/favicons?domain=deepl.com
// @grant        GM_addStyle
// @run-at       document-start
// @updateURL    https://github.com/fauu/Kamite/raw/master/extra/userscripts/Kamite%20DeepL%20mod.user.js
// @downloadURL  https://github.com/fauu/Kamite/raw/master/extra/userscripts/Kamite%20DeepL%20mod.user.js
// ==/UserScript==

(function() {
  "use strict";

  function main() {
    if (window === window.parent) {
      return;
    }
    document.addEventListener("DOMContentLoaded", postLoad);
    preLoad();
  }

  function preLoad() {
    GM_addStyle(`
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
      }
      .dl_body.dl_body--redesign {
        background-color: var(--color-bg) !important;
        min-width: 100vw !important;
        width: 100vw !important;
        max-width: 100vw !important;
        overflow: hidden;
      }
      .lmt__language_container {
        background-color: var(--color-bg3) !important;
        border-top-left-radius: 5px !important;
        border-top-right-radius: 5px !important;
      }
      .lmt__language_select > button * {
        color: var(--color-fg3) !important;
        font-size: 12px !important;
        line-height: 16px !important;
      }
      #dl_translator .lmt__text .lmt__sides_container {
        flex-direction: column !important;
        height: calc(100vh - 10px) !important;
        border: none !important;
        box-shadow: none !important;
      }
      .dl_body.dl_body--translator #dl_translator.lmt--web .lmt__sides_container .lmt__side_container .lmt__textarea_container {
        background-color: var(--color-bg2) !important;
        border: none !important;
        padding-bottom: 20px !important;
        border-bottom-left-radius: 5px !important;
        border-bottom-right-radius: 5px !important;
      }
      .dl_body.dl_body--translator #dl_translator.lmt--web .lmt__sides_container.lmt__sides_container--focus_source .lmt__side_container.lmt__side_container--source .lmt__textarea_container {
        border: 1px solid var(--color-accB2) !important;
      }
      #dl_translator .lmt__sides_container {
        width: 100vw !important;
        display: flex;
        align-items: center;
      }
      #dl_translator .lmt__sides_container .lmt__side_container {
        width: calc(100vw - 10px) !important;
      }
      #dl_translator .lmt__sides_container .lmt__side_container:first-child {
        margin-bottom: 5px !important;
      }
      #dl_translator .lmt__sides_container .lmt__side_container,
      .lmt--web .lmt__textarea_container {
        min-height: 30vh !important;
        height: 40vh !important;
        max-height: 100% !important;
      }
      .lmt__textarea_container .lmt__textarea {
        color: var(--color-fg) !important;
      }
      .lmt__language_container_switch {
        background-color: var(--color-bg3) !important;
        border: 1px solid var(--color-bg3-hl) !important;
        margin: 0 0 40px calc(50vw - 10px - (44px / 2)) !important;
      }
      .lmt__language_container_switch svg path {
        stroke: var(--color-fg3) !important;
      }
      .lmt__language_container_switch--disabled svg path {
        stroke: var(--color-med2) !important;
      }
      .lmt__language_container {
        height: 32px !important;
      }
      .dl_body--redesign .dl_top_element--wide {
        padding: 0 10px 0 0px !important;
      }
      .lmt__translations_as_text::before {
        border-top-color: var(--color-med2) !important;
      }
      .lmt__translations_as_text__text_btn {
        color: var(--color-fg4) !important;
      }
      .lmt__source_textarea__length_marker {
        color: var(--color-med3) !important;
      }
      .lmt__targetLangMenu_extensions button {
        color: var(--color-med3) !important;
        border: none !important;
      }
      .lmt__inner_textarea_container {
        animation: none !important;
        box-shadow: none !important;
      }
      .lmt__target_toolbar {
        display: none !important;
      }
      .lmt__glossary_button_label {
        color: var(--color-fg4) !important;
      }
    `);
  }

  function postLoad() {
    const toRemove = [
      "#dl_cookieBanner",
      ".dl_header",
      ".lmt__docTrans-tab-container",
      ".eSEOtericText",
      "#dl_quotes_container",
      ".lmt__textarea_placeholder_text",
      ".lmt__target_toolbar__share_container",
      ".rg-badge-container-DF-1962",
      "#iosAppAdPortal",
      "footer",
      "#dl_translator > div[dl-attr]",
      "#lmt__dict",
      ".lmt__textarea_separator",
      "#lmt_pro_ad_container"
    ];
    toRemove.map(sel => document.querySelector(sel).remove());

    document.body.scrollTop = 0;
  }

  main();
})();