"use strict";

(function () {
  const NATIVE_APP = "displayfix";
  const MARK = "__displayFixFilter";
  const AREA_THRESHOLD = 0.2;

  let port = null;
  let filterText = "";
  let scheduled = false;

  function connect() {
    try {
      port = browser.runtime.connectNative(NATIVE_APP);
      port.onMessage.addListener((msg) => {
        if (msg && typeof msg.filter === "string") {
          filterText = msg.filter;
          schedule();
        }
      });
      port.onDisconnect.addListener(() => {
        port = null;
        setTimeout(connect, 500);
      });
    } catch (e) {
      port = null;
      setTimeout(connect, 500);
    }
  }

  function collectMedia(root, out) {
    let nodes;
    try {
      nodes = root.querySelectorAll("video, canvas");
    } catch (e) {
      nodes = [];
    }
    for (const node of nodes) {
      out.push(node);
      if (node.shadowRoot) {
        collectMedia(node.shadowRoot, out);
      }
    }
  }

  function isVisible(el) {
    const rects = el.getClientRects();
    if (!rects.length) {
      return false;
    }
    const r = rects[0];
    return r.width > 4 && r.height > 4;
  }

  function qualifies(el) {
    if (!isVisible(el)) {
      return false;
    }
    if (document.fullscreenElement &&
        (document.fullscreenElement === el || document.fullscreenElement.contains(el))) {
      return true;
    }
    const r = el.getClientRects()[0];
    const area = r.width * r.height;
    const viewport = window.innerWidth * window.innerHeight;
    return area >= AREA_THRESHOLD * viewport;
  }

  function applyTo(el) {
    if (filterText) {
      if (el.style.filter !== filterText) {
        el.style.filter = filterText;
      }
      el[MARK] = true;
    } else if (el[MARK]) {
      el.style.filter = "";
      el[MARK] = false;
    }
  }

  function sweep() {
    const media = [];
    collectMedia(document, media);
    for (const el of media) {
      if (qualifies(el)) {
        applyTo(el);
      } else if (el[MARK]) {
        el.style.filter = "";
        el[MARK] = false;
      }
    }
  }

  function schedule() {
    if (scheduled) {
      return;
    }
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      try {
        sweep();
      } catch (e) {}
    });
  }

  function observe() {
    const observer = new MutationObserver(() => schedule());
    observer.observe(document, { childList: true, subtree: true, attributes: true });
    window.addEventListener("resize", schedule);
    window.addEventListener("fullscreenchange", schedule);
    setInterval(schedule, 1000);
  }

  connect();

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", observe);
  } else {
    observe();
  }
  schedule();
})();
