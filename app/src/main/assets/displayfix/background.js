"use strict";

(function () {
  const NATIVE_APP = "displayfix";
  let nativePort = null;

  function connect() {
    try {
      nativePort = browser.runtime.connectNative(NATIVE_APP);
      nativePort.onDisconnect.addListener(() => {
        nativePort = null;
        setTimeout(connect, 500);
      });
    } catch (e) {
      nativePort = null;
      setTimeout(connect, 500);
    }
  }

  connect();
})();
