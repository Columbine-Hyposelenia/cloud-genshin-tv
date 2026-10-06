"use strict";

const NATIVE_APP = "displayfix";
let nativePort = null;
let currentFilter = "";

function connectNative() {
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
    nativePort.onMessage.addListener((msg) => {
      if (!msg) {
        return;
      }
      if (typeof msg.filter === "string") {
        currentFilter = msg.filter;
        applyFilter();
      }
      if (msg.report) {
        collectTelemetry();
      }
    });
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
      setTimeout(connectNative, 500);
    });
  } catch (e) {
    nativePort = null;
    setTimeout(connectNative, 500);
  }
}

async function activeTabId() {
  try {
    const tabs = await browser.tabs.query({});
    if (tabs && tabs.length) {
      return tabs[tabs.length - 1].id;
    }
  } catch (e) {
  }
  return null;
}

function pageApply(filter) {
  const MARK = "__displayFixFilter";
  const STYLE_ID = "__displayFixStyle";
  const AREA = 0.2;

  function isVisible(el) {
    const rects = el.getClientRects();
    if (!rects.length) {
      return false;
    }
    return rects[0].width > 4 && rects[0].height > 4;
  }

  function collect(root, out) {
    let nodes;
    try {
      nodes = root.querySelectorAll("video,canvas");
    } catch (e) {
      nodes = [];
    }
    for (const node of nodes) {
      out.push(node);
      if (node.shadowRoot) {
        collect(node.shadowRoot, out);
      }
    }
  }

  const media = [];
  collect(document, media);
  let applied = false;

  for (const el of media) {
    let qualify = false;
    if (isVisible(el)) {
      const rect = el.getClientRects()[0];
      const area = rect.width * rect.height;
      const viewport = innerWidth * innerHeight;
      qualify = area >= AREA * viewport;
      const fs = document.fullscreenElement;
      if (fs && (fs === el || fs.contains(el))) {
        qualify = true;
      }
    }
    if (qualify && filter) {
      el.style.filter = filter;
      el[MARK] = true;
      applied = true;
    } else if (el[MARK]) {
      el.style.filter = "";
      el[MARK] = false;
    }
  }

  const fsEl = document.fullscreenElement;
  if (fsEl && fsEl.tagName !== "VIDEO" && fsEl.tagName !== "CANVAS" && filter) {
    fsEl.style.filter = filter;
    applied = true;
  }

  let style = document.getElementById(STYLE_ID);
  if (filter) {
    if (!style) {
      style = document.createElement("style");
      style.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = ":fullscreen,:fullscreen video,:fullscreen canvas,"
      + "video:fullscreen,canvas:fullscreen{filter:" + filter + " !important;}";
  } else if (style) {
    style.textContent = "";
  }

  return { url: location.href, count: media.length, applied: applied };
}

function pageTelemetry() {
  const SAMPLE_W = 32;
  const SAMPLE_H = 18;

  function collect(root, out) {
    let nodes;
    try {
      nodes = root.querySelectorAll("video,canvas");
    } catch (e) {
      nodes = [];
    }
    for (const node of nodes) {
      out.push(node);
      if (node.shadowRoot) {
        collect(node.shadowRoot, out);
      }
    }
  }

  function lumaFrom(data) {
    let sum = 0;
    let max = 0;
    const count = data.length / 4;
    for (let i = 0; i < data.length; i += 4) {
      const y = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
      sum += y;
      if (y > max) {
        max = y;
      }
    }
    return { mean: Math.round(sum / count), max: Math.round(max) };
  }

  function visibleOf(el) {
    const rects = el.getClientRects();
    return rects.length > 0 && rects[0].width > 4 && rects[0].height > 4;
  }

  const media = [];
  collect(document, media);
  let sampleCanvas = null;
  let sampleCtx = null;

  const described = media.map((el) => {
    const rect = el.getBoundingClientRect();
    const computed = getComputedStyle(el);
    const info = {
      tag: el.tagName.toLowerCase(),
      width: Math.round(rect.width),
      height: Math.round(rect.height),
      visible: visibleOf(el),
      cssFilter: computed.filter,
      opacity: computed.opacity
    };
    if (el.tagName === "VIDEO") {
      info.readyState = el.readyState;
      info.videoWidth = el.videoWidth;
      info.videoHeight = el.videoHeight;
      info.paused = el.paused;
      info.muted = el.muted;
      info.error = el.error ? el.error.code : null;
      if (el.readyState >= 2 && el.videoWidth > 0) {
        try {
          if (!sampleCanvas) {
            sampleCanvas = document.createElement("canvas");
            sampleCanvas.width = SAMPLE_W;
            sampleCanvas.height = SAMPLE_H;
            sampleCtx = sampleCanvas.getContext("2d");
          }
          sampleCtx.clearRect(0, 0, SAMPLE_W, SAMPLE_H);
          sampleCtx.drawImage(el, 0, 0, SAMPLE_W, SAMPLE_H);
          info.luma = lumaFrom(sampleCtx.getImageData(0, 0, SAMPLE_W, SAMPLE_H).data);
        } catch (e) {
          info.luma = null;
        }
      } else {
        info.luma = null;
      }
    }
    return info;
  });

  return {
    top: window.top === window,
    url: location.href,
    readyState: document.readyState,
    innerWidth: innerWidth,
    innerHeight: innerHeight,
    fullscreen: document.fullscreenElement
      ? document.fullscreenElement.tagName.toLowerCase() : null,
    filterApplied: described.some((m) => m.cssFilter && m.cssFilter !== "none"),
    media: described
  };
}

async function runInPage(fn, value) {
  const tabId = await activeTabId();
  const arg = value === undefined ? "" : JSON.stringify(value);
  const code = "(" + fn.toString() + ")(" + arg + ")";
  const results = await browser.tabs.executeScript(tabId, {
    code: code,
    allFrames: true,
    matchAboutBlank: true
  });
  return results || [];
}

function postNative(obj) {
  if (nativePort) {
    try {
      nativePort.postMessage(obj);
    } catch (e) {
    }
  }
}

async function applyFilter() {
  try {
    await runInPage(pageApply, currentFilter);
  } catch (e) {
    postNative({ injectError: String(e) });
  }
}

async function collectTelemetry() {
  try {
    const results = await runInPage(pageTelemetry);
    let chosen = null;
    for (const result of results) {
      if (result && result.media && result.media.length) {
        chosen = result;
        break;
      }
      if (!chosen) {
        chosen = result;
      }
    }
    if (chosen) {
      postNative({ telemetry: chosen });
    }
  } catch (e) {
    postNative({ injectError: String(e) });
  }
}

try {
  browser.tabs.onUpdated.addListener((tabId, info) => {
    if (info.status === "complete") {
      applyFilter();
    }
  });
} catch (e) {
}

setInterval(applyFilter, 1500);
setInterval(collectTelemetry, 2500);
setTimeout(applyFilter, 800);
setTimeout(collectTelemetry, 1200);

connectNative();
