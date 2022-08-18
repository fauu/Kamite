export function integrateClipboardInserter(onText: (text: string) => void) {
  const bodyEl = document.getElementsByTagName("body")[0];
  const observer = new MutationObserver(mutations => {
    mutations.forEach(m => {
      m.addedNodes.forEach(n => {
        if (n.nodeName === "P" && n.textContent !== null) {
          onText(n.textContent);
          n.parentNode?.removeChild(n);
        }
      });
    });
  });
  observer.observe(bodyEl, { childList: true });
}
