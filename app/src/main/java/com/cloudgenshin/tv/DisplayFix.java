package com.cloudgenshin.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.concurrent.CopyOnWriteArrayList;

public final class DisplayFix {
    public static final int PRESET_COUNT = 6;
    public static final int GEOMETRY_COUNT = 12;

    private static final String EXT_ID = "displayfix@cloudgenshin.tv";
    private static final String NATIVE_APP = "displayfix";
    private static final String EXT_URI = "resource://android/assets/displayfix/";
    private static final String PREFS_NAME = "display_fix";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_GEOMETRY = "geometry";

    private static final float[][] PRESETS = {
            null,
            {1.6f, 1.00f},
            {2.2f, 1.00f},
            {2.8f, 1.02f},
            {3.5f, 1.05f},
            {4.5f, 1.08f}
    };

    private static final float[] GEOMETRY_SCALES = {
            0f,
            1.5f, 2.0f, 2.5f, 3.0f, 4.0f,
            5.0f, 6.0f, 8.0f, 10.0f, 12.0f, 16.0f
    };

    private static volatile int sPreset;
    private static volatile int sGeometry;
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
        sPreset = clamp(prefs.getInt(KEY_PRESET, 0), PRESET_COUNT);
        sGeometry = clamp(prefs.getInt(KEY_GEOMETRY, 0), GEOMETRY_COUNT);
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

    public void setPreset(final Context context, final int preset) {
        sPreset = clamp(preset, PRESET_COUNT);
        persist(context);
        broadcastState();
        requestTelemetry();
    }

    public void cyclePreset(final Context context) {
        setPreset(context, sPreset + 1 >= PRESET_COUNT ? 0 : sPreset + 1);
    }

    public void cycleGeometry(final Context context) {
        sGeometry = sGeometry + 1 >= GEOMETRY_COUNT ? 0 : sGeometry + 1;
        persist(context);
        broadcastState();
        requestTelemetry();
    }

    public void reattachVideo() {
        broadcast("reattach", true);
        requestTelemetry();
    }

    public void replaceVideo() {
        broadcast("replace", true);
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

    public static int preset() {
        return sPreset;
    }

    public static boolean extensionLoaded() {
        return sExtensionLoaded;
    }

    public static boolean portConnected() {
        return sPortConnected;
    }

    public static String presetLabel() {
        if (sPreset == 0) {
            return "亮度补偿：关闭";
        }
        return "亮度补偿：" + sPreset + " 档";
    }

    public static String geometryLabel() {
        if (sGeometry == 0) {
            return "画面缩放：关闭";
        }
        return "画面缩放：×" + formatScale(GEOMETRY_SCALES[sGeometry]);
    }

    private void broadcastState() {
        for (WebExtension.Port port : mPorts) {
            broadcastState(port);
        }
    }

    private void broadcastState(final WebExtension.Port port) {
        try {
            JSONObject message = new JSONObject();
            message.put("filter", filterFor(sPreset));
            message.put("geometry", geometryFor(sGeometry));
            port.postMessage(message);
        } catch (Exception ignored) {
        }
    }

    private void broadcast(final String key, final Object value) {
        for (WebExtension.Port port : mPorts) {
            postToPort(port, key, value);
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

    private static String filterFor(final int preset) {
        if (preset <= 0) {
            return "";
        }
        float[] values = PRESETS[preset];
        return "brightness(" + values[0] + ") contrast(" + values[1] + ")";
    }

    private static String geometryFor(final int geometry) {
        if (geometry <= 0) {
            return "";
        }
        return "scale(" + formatScale(GEOMETRY_SCALES[geometry]) + ")";
    }

    private static String formatScale(final float scale) {
        if (scale == Math.rint(scale)) {
            return String.valueOf((int) scale);
        }
        return String.valueOf(scale);
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
                .putInt(KEY_PRESET, sPreset)
                .putInt(KEY_GEOMETRY, sGeometry)
                .apply();
    }
}
