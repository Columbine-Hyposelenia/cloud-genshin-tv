"use strict";

(function () {
  const NATIVE_APP = "displayfix";
  const MARK = "__displayFixFilter";
  const AREA_THRESHOLD = 0.2;
  const SAMPLE_W = 32;
  const SAMPLE_H = 18;

  let port = null;
  let filterText = "";
  let scheduled = false;
  let filterApplied = false;
  let sampleCanvas = null;
  let sampleCtx = null;
  let fixStyle = null;
  let ancestorEl = null;

  function connect() {
    try {
      port = browser.runtime.connectNative(NATIVE_APP);
      port.onMessage.addListener((msg) => {
        if (!msg) {
          return;
        }
        if (typeof msg.filter === "string") {
          filterText = msg.filter;
          schedule();
        }
        if (msg.report) {
          sendTelemetry();
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
    let applied = false;
    for (const el of media) {
      if (qualifies(el)) {
        applyTo(el);
        trackFrames(el);
      } else if (el[MARK]) {
        el.style.filter = "";
        el[MARK] = false;
      }
      if (el[MARK]) {
        applied = true;
      }
    }
    if (applyFullscreenAncestor()) {
      applied = true;
    }
    updateStyle();
    filterApplied = applied;
  }

  function applyFullscreenAncestor() {
    const fs = document.fullscreenElement;
    if (!fs || fs.tagName === "VIDEO" || fs.tagName === "CANVAS") {
      clearAncestor();
      return false;
    }
    if (ancestorEl && ancestorEl !== fs) {
      clearAncestor();
    }
    ancestorEl = fs;
    if (filterText) {
      fs.style.filter = filterText;
      return true;
    }
    return false;
  }

  function clearAncestor() {
    if (ancestorEl) {
      ancestorEl.style.filter = "";
      ancestorEl = null;
    }
  }

  function ensureFixStyle() {
    if (!fixStyle || !fixStyle.parentNode) {
      fixStyle = document.createElement("style");
      fixStyle.id = "__displayFixStyle";
      const root = document.head || document.documentElement;
      root.appendChild(fixStyle);
    }
    return fixStyle;
  }

  function updateStyle() {
    const style = ensureFixStyle();
    style.textContent = filterText
      ? (":fullscreen, :fullscreen video, :fullscreen canvas, "
         + "video:fullscreen, canvas:fullscreen { filter: "
         + filterText + " !important; }")
      : "";
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

  function trackFrames(el) {
    if (el.tagName !== "VIDEO" || el.__rVFCStarted) {
      return;
    }
    el.__rVFCStarted = true;
    el.__frameCount = 0;
    if (typeof el.requestVideoFrameCallback === "function") {
      const onFrame = () => {
        el.__frameCount++;
        try {
          el.requestVideoFrameCallback(onFrame);
        } catch (e) {}
      };
      try {
        el.requestVideoFrameCallback(onFrame);
      } catch (e) {}
    }
  }

  function ensureSampler() {
    if (!sampleCanvas) {
      sampleCanvas = document.createElement("canvas");
      sampleCanvas.width = SAMPLE_W;
      sampleCanvas.height = SAMPLE_H;
      sampleCtx = sampleCanvas.getContext("2d");
    }
    return sampleCtx;
  }

  function lumaFromPixels(data) {
    let sumY = 0;
    let maxY = 0;
    const count = data.length / 4;
    for (let i = 0; i < data.length; i += 4) {
      const y = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
      sumY += y;
      if (y > maxY) {
        maxY = y;
      }
    }
    return {mean: Math.round(sumY / count), max: Math.round(maxY)};
  }

  function videoLuma(video) {
    if (video.readyState < 2 || video.videoWidth === 0) {
      return null;
    }
    const ctx = ensureSampler();
    if (!ctx) {
      return null;
    }
    try {
      ctx.clearRect(0, 0, SAMPLE_W, SAMPLE_H);
      ctx.drawImage(video, 0, 0, SAMPLE_W, SAMPLE_H);
      const frame = ctx.getImageData(0, 0, SAMPLE_W, SAMPLE_H).data;
      return lumaFromPixels(frame);
    } catch (e) {
      return null;
    }
  }

  function canvasLuma(canvas) {
    let ctx = null;
    try {
      ctx = canvas.getContext("2d");
    } catch (e) {
      ctx = null;
    }
    if (ctx) {
      try {
        const w = Math.min(canvas.width || SAMPLE_W, SAMPLE_W);
        const h = Math.min(canvas.height || SAMPLE_H, SAMPLE_H);
        const frame = ctx.getImageData(0, 0, w, h).data;
        return {kind: "2d", luma: lumaFromPixels(frame)};
      } catch (e) {}
    }
    let gl = null;
    try {
      gl = canvas.getContext("webgl2") || canvas.getContext("webgl");
    } catch (e) {
      gl = null;
    }
    if (gl) {
      try {
        const w = gl.drawingBufferWidth;
        const h = gl.drawingBufferHeight;
        const px = new Uint8Array(4);
        gl.readPixels(Math.floor(w / 2), Math.floor(h / 2), 1, 1,
                      0x1908, 0x1401, px);
        return {kind: "webgl", center: [px[0], px[1], px[2]]};
      } catch (e) {}
    }
    return null;
  }

  function trackInfo(video) {
    if (!video.srcObject || typeof video.srcObject.getTracks !== "function") {
      return null;
    }
    try {
      return video.srcObject.getTracks().map((track) => {
        const info = {
          kind: track.kind,
          enabled: track.enabled,
          muted: track.muted,
          state: track.readyState
        };
        try {
          const settings = track.getSettings();
          info.width = settings.width;
          info.height = settings.height;
          info.frameRate = settings.frameRate;
        } catch (e) {}
        return info;
      });
    } catch (e) {
      return null;
    }
  }

  function describe(el) {
    const rect = el.getBoundingClientRect();
    const computed = window.getComputedStyle(el);
    const viewport = window.innerWidth * window.innerHeight;
    const info = {
      tag: el.tagName.toLowerCase(),
      id: el.id || "",
      width: Math.round(rect.width),
      height: Math.round(rect.height),
      areaRatio: viewport ? Math.round((rect.width * rect.height) / viewport * 100) / 100 : 0,
      visible: isVisible(el),
      marked: !!el[MARK],
      cssFilter: computed.filter,
      opacity: computed.opacity,
      visibility: computed.visibility,
      display: computed.display
    };
    if (el.tagName === "VIDEO") {
      info.readyState = el.readyState;
      info.networkState = el.networkState;
      info.videoWidth = el.videoWidth;
      info.videoHeight = el.videoHeight;
      info.currentTime = Math.round(el.currentTime * 10) / 10;
      info.paused = el.paused;
      info.muted = el.muted;
      info.error = el.error ? el.error.code : null;
      info.frames = el.__frameCount || 0;
      info.tracks = trackInfo(el);
      info.luma = videoLuma(el);
    } else {
      info.attrWidth = el.width;
      info.attrHeight = el.height;
      info.sample = canvasLuma(el);
    }
    return info;
  }

  function gatherTelemetry() {
    const media = [];
    collectMedia(document, media);
    return {
      top: window.top === window,
      url: location.href,
      readyState: document.readyState,
      visibility: document.visibilityState,
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio,
      fullscreen: document.fullscreenElement
        ? document.fullscreenElement.tagName.toLowerCase() : null,
      filterApplied: filterApplied,
      media: media.map(describe)
    };
  }

  function sendTelemetry() {
    if (!port) {
      return;
    }
    try {
      sweep();
      port.postMessage({telemetry: gatherTelemetry()});
    } catch (e) {}
  }

  function observe() {
    const observer = new MutationObserver(() => schedule());
    observer.observe(document, {childList: true, subtree: true, attributes: true});
    window.addEventListener("resize", schedule);
    window.addEventListener("fullscreenchange", schedule);
    const trigger = () => sendTelemetry();
    window.addEventListener("loadeddata", trigger, true);
    window.addEventListener("playing", trigger, true);
    window.addEventListener("waiting", trigger, true);
    window.addEventListener("fullscreenchange", trigger);
    setInterval(schedule, 1000);
    setInterval(sendTelemetry, 2000);
  }

  connect();

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", observe);
  } else {
    observe();
  }
  schedule();
  setTimeout(sendTelemetry, 300);
})();
