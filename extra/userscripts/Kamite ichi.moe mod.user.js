// ==UserScript==
// @name         Kamite ichi.moe mod
// @description  Improves ichi.moe user experience when embedded into Kamite
// @version      1.0.0
// @match        https://ichi.moe/*
// @icon         https://www.google.com/s2/favicons?domain=ichi.moe
// @grant        GM_addStyle
// @run-at       document-start
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
      .wrapper {
        padding: 0 1rem !important;
      }
      body {
        background: var(--color-bg) !important;
        color: var(--color-fg) !important;
      }
      h1, h2, h3, h4, h5, h6 {
        color: var(--color-fg) !important;
      }
      #ichiran-text-field {
        background: var(--color-bg3) !important;
        color: var(--color-fg) !important;
        border: 1px solid var(--color-med2);
        margin-bottom: 0 !important;
      }
      .button {
        background: var(--color-med2) !important;
      }
      #div-ichiran-form {
        margin-bottom: 0 !important;
      }
      .highlight {
        background: none !important;
        color: var(--color-accA) !important;
      }
      span.query-word:hover {
        border-bottom-color: var(--color-accB2-hl) !important;
      }
      .gloss-rtext a.info-link {
        color: var(--color-accA) !important;
      }
      .gloss {
        background-color: var(--color-bg2) !important;
        border-color: var(--color-bg4) !important;
      }
      span.gloss-desc {
        font-size: .9rem; !important;
      }
      .pos-desc {
        color: var(--color-fg5) !important;
      }
      .has-tip {
        color: var(--color-fg5) !important;
      }
      .gloss-row:not(.hidden) ~ .gloss-row:not(.hidden) {
        margin-top: 0.1rem !important;
      }
      .f-dropdown::before {
        border-color: transparent transparent #a8a5a3 transparent !important;
      }
      .f-dropdown li:hover, .f-dropdown li:focus {
        background: var(--color-fg5) !important;
      }
      .f-dropdown {
        background: var(--color-med2) !important;
        border: 1px solid var(--color-fg5) !important;
      }
      .query-pick {
        color: var(--color-fg1) !important;
      }
      .note-skipped a {
        color: var(--color-fg5) !important;
      }
      .conj-formal {
        color: var(--color-fg5) !important;
      }
    `);
  }

  function postLoad() {
    const toRemove = [
      ".header",
      "footer",
    ];
    toRemove.map(sel => document.querySelector(sel).remove());

    document.body.scrollTop = 0;
  }

  main();
})();
