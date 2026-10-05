package com.cloudgenshin.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

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

    private final GeckoRuntime mRuntime;
    private WebExtension.Port mPort;

    public DisplayFix(GeckoRuntime runtime, Context context) {
        mRuntime = runtime;
        sPreset = clamp(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PRESET, 0));
    }

    public void attach() {
        mRuntime.getWebExtensionController()
                .ensureBuiltIn(EXT_URI, EXT_ID)
                .accept(extension -> {
                    extension.setMessageDelegate(new WebExtension.MessageDelegate() {
                        @Override
                        public void onConnect(final WebExtension.Port port) {
                            mPort = port;
                            port.setDelegate(new WebExtension.PortDelegate() {
                                @Override
                                public void onDisconnect(final WebExtension.Port disconnected) {
                                    if (disconnected == mPort) {
                                        mPort = null;
                                    }
                                }
                            });
                            postCurrent();
                        }
                    }, NATIVE_APP);
                }, error -> {
                });
    }

    public void setPreset(final Context context, final int preset) {
        sPreset = clamp(preset);
        persist(context);
        postCurrent();
    }

    public static int preset() {
        return sPreset;
    }

    public static String presetLabel() {
        if (sPreset == 0) {
            return "亮度补偿：关闭";
        }
        return "亮度补偿：" + sPreset + " 档";
    }

    private void postCurrent() {
        if (mPort == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("filter", filterFor(sPreset));
            mPort.postMessage(message);
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
