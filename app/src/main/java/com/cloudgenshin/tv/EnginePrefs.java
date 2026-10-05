package com.cloudgenshin.tv;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EnginePrefs {
    private static final String FILE_NAME = "engine_config.yaml";

    private EnginePrefs() {
    }

    public static String prepare(Context context) {
        Map<String, Object> prefs = new LinkedHashMap<String, Object>();

        prefs.put("media.webrtc.hw.h264.enabled", DecoderMode.hardwareDecode());
        prefs.put("media.hardware-video-decoding.enabled", DecoderMode.hardwareDecode());
        prefs.put("media.navigator.mediadatadecoder_h264_enabled", DecoderMode.hardwareDecode());

        prefs.put("media.autoplay.default", 0);
        prefs.put("media.autoplay.blocking_policy", 0);
        prefs.put("media.autoplay.block-webaudio", false);
        prefs.put("media.allowed-to-play.enabled", true);

        prefs.put("media.peerconnection.video.h264_enabled", true);
        prefs.put("media.peerconnection.video.vp9_enabled", true);
        prefs.put("media.peerconnection.simulcast", false);

        prefs.put("webgl.disabled", false);
        prefs.put("webgl.enable-webgl2", true);
        prefs.put("webgl.force-enabled", true);

        int graphics = EngineMode.graphics();
        prefs.put("gfx.webrender.software", graphics == EngineMode.GRAPHICS_SOFTWARE);
        if (graphics == EngineMode.GRAPHICS_NO_COMPOSITOR) {
            prefs.put("gfx.webrender.compositor", false);
        }

        prefs.put("gfx.color_management.mode", 2);
        prefs.put("gfx.color_management.enablev4", true);

        prefs.put("full-screen-api.enabled", true);
        prefs.put("full-screen-api.allow-trusted-requests-only", false);

        File file = new File(context.getFilesDir(), FILE_NAME);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            StringBuilder builder = new StringBuilder();
            builder.append("prefs:\n");
            for (Map.Entry<String, Object> entry : prefs.entrySet()) {
                builder.append("  ").append(entry.getKey()).append(": ");
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    builder.append(((Boolean) value).booleanValue() ? "true" : "false");
                } else {
                    builder.append(value);
                }
                builder.append('\n');
            }
            out.write(builder.toString().getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
        return file.getAbsolutePath();
    }
}
