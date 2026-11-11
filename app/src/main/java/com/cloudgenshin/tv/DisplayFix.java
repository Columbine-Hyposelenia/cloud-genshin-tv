package com.cloudgenshin.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.concurrent.CopyOnWriteArrayList;

public final class DisplayFix {
    public static final int REVEAL_COUNT = 2;

    private static final String EXT_ID = "displayfix@cloudgenshin.tv";
    private static final String NATIVE_APP = "displayfix";
    private static final String EXT_URI = "resource://android/assets/displayfix/";
    private static final String PREFS_NAME = "display_fix";
    private static final String KEY_REVEAL = "reveal";

    private static volatile int sRevealMode;
    private static volatile boolean sExtensionLoaded;
    private static volatile boolean sPortConnected;

    private final GeckoRuntime mRuntime;
    private final CopyOnWriteArrayList<WebExtension.Port> mPorts =
            new CopyOnWriteArrayList<WebExtension.Port>();
    private WebExtension mExtension;

    private final WebExtension.MessageDelegate mMessageDelegate =
            new WebExtension.MessageDelegate() {
                @Override
                public void onConnect(final WebExtension.Port port) {
                    mPorts.addIfAbsent(port);
                    sPortConnected = true;
                    port.setDelegate(new WebExtension.PortDelegate() {
                        @Override
                        public void onPortMessage(final Object message,
                                final WebExtension.Port from) {
                            handleMessage(message);
                        }

                        @Override
                        public void onDisconnect(final WebExtension.Port disconnected) {
                            mPorts.remove(disconnected);
                            sPortConnected = !mPorts.isEmpty();
                        }
                    });
                    broadcastState(port);
                    postToPort(port, "report", true);
                }
            };

    public DisplayFix(GeckoRuntime runtime, Context context) {
        mRuntime = runtime;
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sRevealMode = clamp(prefs.getInt(KEY_REVEAL, 0), REVEAL_COUNT);
    }

    public void start(final Runnable onReady) {
        mRuntime.getWebExtensionController()
                .ensureBuiltIn(EXT_URI, EXT_ID)
                .accept(extension -> {
                    sExtensionLoaded = true;
                    mExtension = extension;
                    extension.setMessageDelegate(mMessageDelegate, NATIVE_APP);
                    onReady.run();
                }, error -> {
                    sExtensionLoaded = false;
                    onReady.run();
                });
    }

    public void bindToSession(final GeckoSession session) {
        if (mExtension != null && session != null) {
            session.getWebExtensionController()
                    .setMessageDelegate(mExtension, mMessageDelegate, NATIVE_APP);
        }
    }

    public void cycleReveal(final Context context) {
        sRevealMode = sRevealMode + 1 >= REVEAL_COUNT ? 0 : sRevealMode + 1;
        persist(context);
        broadcastState();
        requestTelemetry();
    }

    private void handleMessage(final Object message) {
        if (!(message instanceof JSONObject)) {
            return;
        }
        JSONObject json = (JSONObject) message;
        if (!json.has("telemetry")) {
            return;
        }
        JSONObject telemetry = json.optJSONObject("telemetry");
        if (telemetry == null) {
            return;
        }
        boolean isTop = telemetry.optBoolean("top", false);
        JSONObject current = Diag.telemetry();
        if (isTop || current == null || !current.optBoolean("top", false)) {
            Diag.setTelemetry(telemetry);
        }
    }

    public static boolean extensionLoaded() {
        return sExtensionLoaded;
    }

    public static boolean portConnected() {
        return sPortConnected;
    }

    public static int revealMode() {
        return sRevealMode;
    }

    public static String revealLabel() {
        if (sRevealMode == 1) {
            return "底层画面：透明";
        }
        return "底层画面：遮蔽";
    }

    private void broadcastState() {
        for (WebExtension.Port port : mPorts) {
            broadcastState(port);
        }
    }

    private void broadcastState(final WebExtension.Port port) {
        try {
            JSONObject message = new JSONObject();
            message.put("filter", "");
            message.put("geometry", "");
            message.put("revealMode", sRevealMode);
            port.postMessage(message);
        } catch (Exception ignored) {
        }
    }

    public void requestTelemetry() {
        for (WebExtension.Port port : mPorts) {
            postToPort(port, "report", true);
        }
    }

    private void postToPort(WebExtension.Port port, String key, Object value) {
        try {
            JSONObject message = new JSONObject();
            message.put(key, value);
            port.postMessage(message);
        } catch (Exception ignored) {
        }
    }

    private static int clamp(final int value, final int count) {
        if (value < 0) {
            return 0;
        }
        if (value >= count) {
            return count - 1;
        }
        return value;
    }

    private void persist(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_REVEAL, sRevealMode)
                .apply();
    }
}
