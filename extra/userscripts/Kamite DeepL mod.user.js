// ==UserScript==
// @name         Kamite DeepL mod
// @description  Improves DeepL user experience when embedded into Kamite
// @version      2.0.2
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

    preLoad();
    document.addEventListener("DOMContentLoaded", postLoad);
  }

  function preLoad() {
    addCSS();
  }

  function postLoad() {
    removeBloat();
    setupUI();
  }

  function removeBloat() {
    const toRemove = [
      "#dl_cookieBanner",
      ".dl_header",
      ".lmt__docTrans-tab-container",
      ".eSEOtericText",
      "#dl_quotes_container",
      ".lmt__textarea_placeholder_text",
      ".rg-badge-container-DF-1962",
      "#iosAppAdPortal",
      "footer",
      "#dl_translator > div[dl-attr]",
      ".lmt__textarea_separator",
      "#lmt_pro_ad_container",
      ".dl_footerV2_container",
    ];
    toRemove.map(sel => {
      const el = document.querySelector(sel);
      el && el.remove();
    });
  }

  function setupUI() {
    document.body.className = "";
    document.querySelector(".dl_translator_page_container").style.display = "none";

    const inputTextareaEl = document.querySelector(".lmt__source_textarea");

    const inputContainerEl = document.createElement("div");
    inputContainerEl.className = "input-container";

    const inputEl = inputTextareaEl.querySelector("div[contenteditable=true]");
    inputEl.className = "input";
    inputEl.addEventListener("input", () => inputTextareaEl.dispatchEvent(new Event("input")));

    inputContainerEl.append(inputEl);
    document.body.append(inputContainerEl);

    const outputEl = document.createElement("div");
    outputEl.className = "output";
    document.body.append(outputEl);

    copyOutputsTo(outputEl);
  }

  function copyOutputsTo(outputEl) {
    const handleTargetMutation = () => {
      outputEl.innerHTML = "";
      for (const el of document.querySelectorAll(".lmt__translations_as_text__text_btn")) {
        const altEl = document.createElement("div");
        altEl.className = "output-alternative";
        altEl.textContent = el.textContent;
        outputEl.append(altEl);
      }
    };
    const targetElObserver = new MutationObserver(handleTargetMutation);
    targetElObserver.observe(
      document.querySelector("d-textarea > div"),
      { childList: true }
    );
  }

  function addCSS() {
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

        --font-stack: var(--font-ui), var(--font-jp), 'Noto Sans CJK JP', 'Hiragino Sans',
              'Hiragino Kaku Gothic Pro', '游ゴシック' , '游ゴシック体', YuGothic, 'Yu Gothic',
              'ＭＳ ゴシック' , 'MS Gothic', 'Segoe UI', Helvetica, Ubuntu, Cantarell, Arial,
              sans-serif;
        --border-radius-default: 2px;
        --shadow-panel: rgba(0, 0, 0, 0.07) 0px 1px 2px, rgba(0, 0, 0, 0.07) 0px 2px 4px, rgba(0, 0, 0, 0.07) 0px 4px 8px, rgba(0, 0, 0, 0.07) 0px 8px 16px, rgba(0, 0, 0, 0.07) 0px 16px 32px, rgba(0, 0, 0, 0.07) 0px 32px 64px;
      }

      body {
        font-family: var(--font-stack);
        background: var(--color-bg);
        color: var(--color-fg);
        margin: 1rem 0.25rem;
        letter-spacing: -0.02rem;
      }

      .input-container {
        background: var(--color-bg2);
        border: 1px solid var(--color-bg2-hl);
        height: 10rem;
        box-shadow: rgba(0, 0, 0, 0.04) 0px 1px 2px, rgba(0, 0, 0, 0.04) 0px 2px 4px, rgba(0, 0, 0, 0.04) 0px 4px 8px, rgba(0, 0, 0, 0.04) 0px 8px 16px, rgba(0, 0, 0, 0.04) 0px 16px 32px, rgba(0, 0, 0, 0.04) 0px 32px 64px;
      }

      .input-container, .output {
        width: 90vw;
        margin: 0 auto;
        margin-bottom: 0.2rem;
        border-radius: var(--border-radius-default);
        padding: 0.45rem 0.6rem;
        line-height: 1.33;
      }

      .input {
        font-size: 1.8rem;
        width: 100%;
        height: 100%;
        background: var(--color-bg2);
        resize: none;
      }

      .output {
        font-size: 1.5rem;
      }

      .output-alternative:not(:first-child) {
        font-size: 1.2rem;
        color: var(--color-fg5);
        margin-top: 0.25rem;
      }
    `);
  }

  main();
})();