package com.cloudgenshin.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public final class DeviceInfo {
    private static final Pattern PROP_PATTERN = Pattern.compile(
            "(?i).*(gpu|gfx|gralloc|hwcomposer|egl|opengl|media|codec|omx|sf\\.|"
            + "debug\\.hwui|persist\\.sys|ro\\.hardware|ro\\.product|dalvik\\.vm|"
            + "ro\\.sf|sys\\.usb).*");

    private DeviceInfo() {
    }

    public static String build(Context context, boolean sessionOpen, int keyboardCount,
            String keySummary) {
        StringBuilder out = new StringBuilder();
        GpuInfo gpu = GpuInfo.collect();
        JSONObject telemetry = Diag.telemetry();
        AppLog appLog = AppLog.get();

        header(out, "SELF TEST");
        pass(out, "extension loaded", DisplayFix.extensionLoaded());
        pass(out, "content port connected", DisplayFix.portConnected());
        pass(out, "media elements found", mediaCount(telemetry) > 0);
        pass(out, "brightness filter applied", filterApplied(telemetry));
        pass(out, "log capture running", appLog != null && appLog.isRunning());
        pass(out, "logcat readable", AppLog.snapshot().length() > 0);
        pass(out, "gpu enumerated", gpu != null);
        pass(out, "h264 hardware decoder", h264Decoder() != null);
        pass(out, "session open", sessionOpen);

        header(out, "APPLICATION");
        line(out, "version", version(context));
        line(out, "data directory", context.getApplicationInfo().dataDir);
        line(out, "external files", String.valueOf(context.getExternalFilesDir(null)));
        line(out, "external state", Environment.getExternalStorageState());

        header(out, "DEVICE");
        line(out, "manufacturer", Build.MANUFACTURER);
        line(out, "brand", Build.BRAND);
        line(out, "model", Build.MODEL);
        line(out, "product", Build.PRODUCT);
        line(out, "device", Build.DEVICE);
        line(out, "board", Build.BOARD);
        line(out, "hardware", Build.HARDWARE);
        line(out, "android release", Build.VERSION.RELEASE);
        line(out, "sdk", String.valueOf(Build.VERSION.SDK_INT));
        line(out, "incremental", Build.VERSION.INCREMENTAL);
        line(out, "type", Build.TYPE);
        line(out, "tags", Build.TAGS);
        line(out, "abis", joinAbis());
        line(out, "kernel", System.getProperty("os.version"));

        header(out, "DISPLAY");
        display(context, out);

        header(out, "MEMORY");
        memory(context, out);

        header(out, "GPU / GL");
        if (gpu != null) {
            line(out, "vendor", gpu.vendor);
            line(out, "renderer", gpu.renderer);
            line(out, "version", gpu.version);
            line(out, "GL_OES_EGL_image_external", String.valueOf(gpu.extExternal));
            line(out, "GL_OES_EGL_image_external_essl3", String.valueOf(gpu.extExternalEssl3));
            line(out, "GL_EXT_YUV_target", String.valueOf(gpu.extYuvTarget));
            line(out, "GL_EXT_sRGB_write_control", String.valueOf(gpu.extSrgbWrite));
            wrap(out, gpu.extensions);
        } else {
            line(out, "gpu", "unavailable");
        }

        header(out, "MEDIA CODECS");
        codecs(out);

        header(out, "EFFECTIVE SETTINGS");
        line(out, "render backend", EngineMode.backend() == EngineMode.BACKEND_SURFACE
                ? "SurfaceView" : "TextureView");
        line(out, "graphics mode", graphicsMode());
        line(out, "hardware decode", String.valueOf(DecoderMode.hardwareDecode()));
        line(out, "verbose logging", String.valueOf(Diag.isVerbose(context)));
        if (Diag.isVerbose(context)) {
            line(out, "MOZ_LOG injection", String.valueOf(EnvTool.succeeded()));
        }
        line(out, "brightness preset", DisplayFix.preset() == 0
                ? "off" : String.valueOf(DisplayFix.preset()));
        line(out, "keyboard devices", String.valueOf(keyboardCount));
        if (keySummary != null && keySummary.length() > 0) {
            wrap(out, keySummary);
        }

        header(out, "CONTENT TELEMETRY");
        long age = Diag.telemetryAgeMillis();
        line(out, "telemetry age", age < 0 ? "none" : String.valueOf(age) + " ms");
        if (telemetry != null) {
            try {
                wrap(out, telemetry.toString(2));
            } catch (Exception e) {
                wrap(out, telemetry.toString());
            }
        } else {
            wrap(out, "no telemetry received from content script");
        }

        header(out, "LOG BUFFER");
        wrap(out, AppLog.bufferInfo().trim());
        AppLog liveCapture = AppLog.get();
        if (liveCapture != null) {
            line(out, "live file bytes", String.valueOf(liveCapture.liveFile().length()));
            String liveTail = liveCapture.readFile(liveCapture.liveFile(), 2500);
            wrap(out, liveTail.length() > 0 ? liveTail : "[live file empty]");
        }
        String snapshot = AppLog.snapshot();
        line(out, "snapshot chars", String.valueOf(snapshot.length()));
        if (snapshot.length() > 3000) {
            snapshot = snapshot.substring(snapshot.length() - 3000);
        }
        wrap(out, snapshot);

        header(out, "SYSTEM PROPERTIES (filtered)");
        properties(out);

        return out.toString();
    }

    private static int mediaCount(JSONObject telemetry) {
        if (telemetry == null) {
            return 0;
        }
        try {
            return telemetry.optJSONArray("media") != null
                    ? telemetry.getJSONArray("media").length() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean filterApplied(JSONObject telemetry) {
        if (telemetry == null) {
            return false;
        }
        return telemetry.optBoolean("filterApplied", false);
    }

    private static String h264Decoder() {
        try {
            int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if ("video/avc".equals(type)) {
                        return info.getName();
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static void codecs(StringBuilder out) {
        try {
            int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    continue;
                }
                String[] types = info.getSupportedTypes();
                for (String type : types) {
                    StringBuilder entry = new StringBuilder();
                    entry.append(info.getName()).append(" -> ").append(type);
                    if (type.startsWith("video/")) {
                        entry.append(" colors=").append(colorFormats(info, type));
                    }
                    wrap(out, entry.toString());
                }
            }
        } catch (Exception e) {
            wrap(out, "codec enumeration failed: " + e);
        }
    }

    private static String colorFormats(MediaCodecInfo info, String type) {
        try {
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
            if (caps.colorFormats == null) {
                return "[]";
            }
            StringBuilder colors = new StringBuilder("[");
            for (int c = 0; c < caps.colorFormats.length; c++) {
                if (c > 0) {
                    colors.append(',');
                }
                colors.append("0x").append(Integer.toHexString(caps.colorFormats[c]));
            }
            return colors.append(']').toString();
        } catch (Exception e) {
            return "n/a";
        }
    }

    private static void display(Context context, StringBuilder out) {
        try {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            android.view.Display display = manager.getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getRealSize(size);
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            line(out, "real size", size.x + " x " + size.y);
            line(out, "density", String.valueOf(metrics.density));
            line(out, "densityDpi", String.valueOf(metrics.densityDpi));
            line(out, "refresh rate", String.valueOf(display.getRefreshRate()) + " Hz");
            line(out, "rotation", String.valueOf(display.getRotation()));
        } catch (Exception e) {
            line(out, "display", "unavailable");
        }
    }

    private static void memory(Context context, StringBuilder out) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            line(out, "total", mb(memory.totalMem));
            line(out, "available", mb(memory.availMem));
            line(out, "threshold", mb(memory.threshold));
            line(out, "low memory", String.valueOf(memory.lowMemory));
        } catch (Exception e) {
        }
        Runtime runtime = Runtime.getRuntime();
        line(out, "jvm max", mb((long) runtime.maxMemory()));
        line(out, "jvm total", mb(runtime.totalMemory()));
        line(out, "jvm free", mb(runtime.freeMemory()));
    }

    private static String mb(long bytes) {
        return (bytes / 1024L / 1024L) + " MB";
    }

    private static String graphicsMode() {
        int graphics = EngineMode.graphics();
        if (graphics == EngineMode.GRAPHICS_SOFTWARE) {
            return "software WebRender";
        }
        if (graphics == EngineMode.GRAPHICS_NO_COMPOSITOR) {
            return "hardware, compositor off";
        }
        return "hardware WebRender";
    }

    private static String joinAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) {
            return String.valueOf(Build.CPU_ABI);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(abis[i]);
        }
        return builder.toString();
    }

    private static String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void properties(StringBuilder out) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("getprop");
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (PROP_PATTERN.matcher(line).matches()) {
                    wrap(out, line.trim());
                }
            }
        } catch (Exception e) {
            wrap(out, "getprop failed");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void header(StringBuilder out, String title) {
        out.append('\n').append(title).append('\n');
    }

    private static void line(StringBuilder out, String key, String value) {
        wrap(out, key + ": " + value);
    }

    private static void pass(StringBuilder out, String label, boolean ok) {
        wrap(out, (ok ? "[PASS] " : "[FAIL] ") + label);
    }

    private static void wrap(StringBuilder out, String text) {
        out.append(text).append('\n');
    }
}
