package com.cloudgenshin.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.util.concurrent.CopyOnWriteArrayList;

public final class DisplayFix {
    public static final int PRESET_COUNT = 6;

    private static final String EXT_ID = "displayfix@cloudgenshin.tv";
    private static final String NATIVE_APP = "displayfix";
    private static final String EXT_URI = "resource://android/assets/displayfix/";
    private static final String PREFS_NAME = "display_fix";
    private static final String KEY_PRESET = "preset";

    private static final float[][] PRESETS = {
            null,
            {1.6f, 1.00f},
            {2.2f, 1.00f},
            {2.8f, 1.02f},
            {3.5f, 1.05f},
            {4.5f, 1.08f}
    };

    private static volatile int sPreset;
    private static volatile boolean sExtensionLoaded;
    private static volatile boolean sPortConnected;

    private final GeckoRuntime mRuntime;
    private final CopyOnWriteArrayList<WebExtension.Port> mPorts =
            new CopyOnWriteArrayList<WebExtension.Port>();

    public DisplayFix(GeckoRuntime runtime, Context context) {
        mRuntime = runtime;
        sPreset = clamp(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PRESET, 0));
    }

    public void attach() {
        mRuntime.getWebExtensionController()
                .ensureBuiltIn(EXT_URI, EXT_ID)
                .accept(extension -> {
                    sExtensionLoaded = true;
                    extension.setMessageDelegate(new WebExtension.MessageDelegate() {
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
                            postToPort(port, "filter", filterFor(sPreset));
                            postToPort(port, "report", true);
                        }
                    }, NATIVE_APP);
                }, error -> {
                    sExtensionLoaded = false;
                });
    }

    public void setPreset(final Context context, final int preset) {
        sPreset = clamp(preset);
        persist(context);
        broadcastFilter();
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

    private void broadcastFilter() {
        for (WebExtension.Port port : mPorts) {
            postToPort(port, "filter", filterFor(sPreset));
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

    private static int clamp(final int preset) {
        if (preset < 0) {
            return 0;
        }
        if (preset >= PRESET_COUNT) {
            return PRESET_COUNT - 1;
        }
        return preset;
    }

    private static void persist(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_PRESET, sPreset).apply();
    }
}
