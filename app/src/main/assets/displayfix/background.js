"use strict";

const NATIVE_APP = "displayfix";
let nativePort = null;
let currentFilter = "";
let currentGeometry = "";
let currentRevealMode = 0;

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
      if (typeof msg.geometry === "string") {
        currentGeometry = msg.geometry;
        applyFilter();
      }
      if (typeof msg.revealMode === "number") {
        currentRevealMode = msg.revealMode;
        applyFilter();
      }
      if (msg.reattach) {
        reattachVideo();
      }
      if (msg.replace) {
        replaceVideo();
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

function pageApply(filter, geometry, revealMode) {
  const MARK_FILTER = "__displayFixFilter";
  const MARK_GEOMETRY = "__displayFixGeometry";
  const MARK_REVEAL = "__displayFixReveal";
  const STYLE_ID = "__displayFixStyle";
  const REVEAL_STYLE_ID = "__displayFixRevealStyle";
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
      el[MARK_FILTER] = true;
      applied = true;
    } else if (el[MARK_FILTER]) {
      el.style.filter = "";
      el[MARK_FILTER] = false;
    }

    if (qualify && geometry) {
      el.style.transformOrigin = "0 0";
      el.style.transform = geometry;
      el.style.objectFit = "fill";
      el[MARK_GEOMETRY] = true;
    } else if (el[MARK_GEOMETRY]) {
      el.style.transform = "";
      el.style.transformOrigin = "";
      el.style.objectFit = "";
      el[MARK_GEOMETRY] = false;
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

  let revealStyle = document.getElementById(REVEAL_STYLE_ID);
  const mainVideo = media.length ? media[0] : document.querySelector("video");
  if (revealMode > 0) {
    if (mainVideo) {
      mainVideo[MARK_REVEAL] = true;
      if (revealMode === 1) {
        mainVideo.style.opacity = "0";
      } else if (revealMode === 2) {
        mainVideo.style.position = "fixed";
        mainVideo.style.left = "0";
        mainVideo.style.top = "0";
        mainVideo.style.margin = "0";
        mainVideo.style.transform = "translateX(-1500px)";
      } else if (revealMode === 3) {
        mainVideo.style.position = "fixed";
        mainVideo.style.left = "0";
        mainVideo.style.top = "0";
        mainVideo.style.margin = "0";
        mainVideo.style.width = "2px";
        mainVideo.style.height = "2px";
      } else if (revealMode === 4) {
        mainVideo.style.display = "none";
      }
    }
    if (!revealStyle) {
      revealStyle = document.createElement("style");
      revealStyle.id = REVEAL_STYLE_ID;
      (document.head || document.documentElement).appendChild(revealStyle);
    }
    revealStyle.textContent = "html,body,body *{background:transparent !important;}";
  } else {
    if (revealStyle) {
      revealStyle.textContent = "";
    }
    for (const el of media) {
      if (el[MARK_REVEAL]) {
        el.style.opacity = "";
        el.style.position = "";
        el.style.left = "";
        el.style.top = "";
        el.style.margin = "";
        el.style.transform = "";
        el.style.width = "";
        el.style.height = "";
        el.style.display = "";
        el[MARK_REVEAL] = false;
      }
    }
  }

  return {
    url: location.href,
    count: media.length,
    applied: applied,
    revealMode: revealMode
  };
}

function pageReattach() {
  const video = document.querySelector("video");
  if (!video) {
    return false;
  }
  const stream = video.srcObject;
  video.srcObject = null;
  if (stream) {
    video.srcObject = stream;
  }
  const play = video.play && video.play();
  if (play && play.catch) {
    play.catch(() => {});
  }
  return true;
}

function pageReplace() {
  const old = document.querySelector("video");
  if (!old || !old.parentNode) {
    return false;
  }
  const stream = old.srcObject;
  const replacement = old.cloneNode(false);
  if (stream) {
    replacement.srcObject = stream;
  }
  replacement.autoplay = true;
  old.parentNode.replaceChild(replacement, old);
  const play = replacement.play && replacement.play();
  if (play && play.catch) {
    play.catch(() => {});
  }
  return true;
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

  function glLumaOf(el) {
    let gl = null;
    try {
      const probe = document.createElement("canvas");
      gl = probe.getContext("webgl") || probe.getContext("experimental-webgl");
      if (!gl || !el.videoWidth) {
        return null;
      }
      const texture = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, el);
      const framebuffer = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0,
        gl.TEXTURE_2D, texture, 0);
      const px = new Uint8Array(4 * SAMPLE_W * SAMPLE_H);
      const ox = Math.floor(el.videoWidth / 2 - SAMPLE_W / 2);
      const oy = Math.floor(el.videoHeight / 2 - SAMPLE_H / 2);
      gl.readPixels(ox, oy, SAMPLE_W, SAMPLE_H, gl.RGBA, gl.UNSIGNED_BYTE, px);
      return lumaFrom(px);
    } catch (e) {
      return null;
    } finally {
      if (gl) {
        const lose = gl.getExtension("WEBGL_lose_context");
        if (lose) {
          lose.loseContext();
        }
      }
    }
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
      transform: computed.transform,
      objectFit: computed.objectFit,
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
        info.glLuma = glLumaOf(el);
      } else {
        info.luma = null;
        info.glLuma = null;
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
    filterApplied: described.some((m) =>
      (m.cssFilter && m.cssFilter !== "none")
      || (m.transform && m.transform !== "none")),
    media: described
  };
}

async function runInPage(fn, a, b) {
  const tabId = await activeTabId();
  const args = [a, b].filter((v) => v !== undefined).map((v) => JSON.stringify(v));
  const code = "(" + fn.toString() + ")(" + args.join(",") + ")";
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
    await runInPage(pageApply, currentFilter, currentGeometry, currentRevealMode);
  } catch (e) {
    postNative({ injectError: String(e) });
  }
}

async function reattachVideo() {
  try {
    await runInPage(pageReattach);
  } catch (e) {
    postNative({ injectError: String(e) });
  }
  setTimeout(applyFilter, 250);
  setTimeout(collectTelemetry, 700);
}

async function replaceVideo() {
  try {
    await runInPage(pageReplace);
  } catch (e) {
    postNative({ injectError: String(e) });
  }
  setTimeout(applyFilter, 350);
  setTimeout(collectTelemetry, 900);
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
