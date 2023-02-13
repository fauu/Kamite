// ==UserScript==
// @name         Kamite One-Click OCR
// @version      0.1.0
// @description  Send OCR requests to Kamite with a single mouse click
// @icon         https://raw.githubusercontent.com/fauu/Kamite/master/src/web/res/favicon/favicon-192.png
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function() {
  "use strict";

  // !!! CONFIG
  const KAMITE_ENDPOINT_BASE = "http://localhost:4110"
  const MOUSE_BUTTON = 1; // 1 = Middle, 2 = Right
  const LONG_CLICK_DELAY_MS = 375;
  // !!!

  const CMD_ENDPOINT = `${KAMITE_ENDPOINT_BASE}/cmd`;

  main();

  function main() {
    let longClickTimeout;
    let wasLongClick = false;

    function handleMousedown(event) {
      if (event.button !== MOUSE_BUTTON) {
        return;
      }
      if (longClickTimeout) {
        clearTimeout(longClickTimeout);
      }
      longClickTimeout = setTimeout(() => {
        wasLongClick = true;
        sendKamiteCMD("ocr/manual-block");
      }, LONG_CLICK_DELAY_MS);
    }

    function handleMouseup(event) {
      if (event.button !== MOUSE_BUTTON) {
        return;
      }
      if (longClickTimeout) {
        clearTimeout(longClickTimeout);
      }
      if (wasLongClick) {
        wasLongClick = false;
        return;
      }
      sendKamiteCMD("ocr/auto-block", '{"mode":"instant"}');
    }

    function sendKamiteCMD(name, data) {
      GM.xmlHttpRequest({
        method: "POST",
        url: `${CMD_ENDPOINT}/${name}`,
        data: data,
        headers: {
          "Content-Type": "application/json"
        },
        onload: function(res) {
          if (res.status !== 200) {
            console.error("Got unexpected status code");
          }
        },
        onerror: function() {
          console.error("Request error");
        }
      });
    }

    document.addEventListener("mousedown", handleMousedown);

    document.addEventListener("mouseup", handleMouseup);

    if (MOUSE_BUTTON === 2) {
      document.addEventListener("contextmenu", e => e.preventDefault(), false);
    }
  }
})();