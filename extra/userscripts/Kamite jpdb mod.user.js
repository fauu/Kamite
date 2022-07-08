// ==UserScript==
// @name         Kamite jpdb mod
// @description  Improves jpdb user experience when embedded into Kamite
// @version      1.0.0
// @match        https://jpdb.io/*
// @icon         https://www.google.com/s2/favicons?domain=jpdb.io
// @grant        GM_addStyle
// @run-at       document-start
// ==/UserScript==

(function() {
  "use strict";

  function main() {
    if (window === window.parent) {
      //return;
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
      .tag .top {
        color: var(--color-accA);
      }
    `);
  }

  function postLoad() {
    const toRemove = [
      ".nav",
    ];
    toRemove.map(sel => document.querySelector(sel).remove());

    const cssVars = [
      ["background-color", "var(--color-bg)"],
      ["scrollbar-color", "var(--color-med)"],
      ["highlight-color", "var(--color-bg3)"],
      ["input-focused-border-color", "var(--color-med3)"],
      ["button-focused-border-color", "var(--color-med3)"],
      ["link-color", "var(--color-accB2-hl)"],
      ["outline-input-color", "var(--color-accB2-hl)"],
    ];
    cssVars.forEach(v => document.documentElement.style.setProperty(`--${v[0]}`, v[1]));

    document.getElementsByTagName("html")[0].classList.add("dark-mode");

    document.querySelectorAll(".result.vocabulary .tag.tooltip").forEach(freqEl => {
      freqEl.innerHTML =
        '<span class="top">' + freqEl.textContent + '</span><br>'
        + freqEl.dataset.tooltip.split(" ").join("<br>");
      delete freqEl.dataset.tooltip;
    });
  }

  main();
})();