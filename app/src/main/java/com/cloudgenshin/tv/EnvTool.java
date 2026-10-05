package com.cloudgenshin.tv;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class EnvTool {
    private static final String LOG_MODULES =
            "timestamp,sync,"
            + "AndroidDecoderModule:5,"
            + "MediaPipeline:5,"
            + "RenderThread:5,"
            + "MediaManager:5,"
            + "MediaDecoder:4,"
            + "MediaFormatReader:4,"
            + "WebRTC:4,"
            + "WebrtcVideoSessionConduit:4,"
            + "WebRender:4,"
            + "Compositor:4,"
            + "ImageBridge:4,"
            + "RemoteVideoDecoder:4,"
            + "VideoEngine:4";

    private static volatile boolean sApplied;
    private static volatile boolean sSucceeded;

    private EnvTool() {
    }

    public static void applyVerboseLogging() {
        if (sApplied) {
            return;
        }
        sApplied = true;
        sSucceeded = setViaNative("MOZ_LOG", LOG_MODULES)
                || setViaMap("MOZ_LOG", LOG_MODULES);
    }

    public static boolean succeeded() {
        return sSucceeded;
    }

    private static boolean setViaNative(String key, String value) {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            Method setEnv = processEnvironment.getDeclaredMethod("setEnv",
                    String.class, String.class);
            setEnv.setAccessible(true);
            Object result = setEnv.invoke(null, key, value);
            if (result instanceof Integer) {
                return ((Integer) result).intValue() == 0;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean setViaMap(String key, String value) {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            Map<String, String> environment = null;
            for (String fieldName : new String[] {"VARIABLES", "THE_ENVIRONMENT", "ENVIRONMENT"}) {
                try {
                    Field field = processEnvironment.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object candidate = field.get(null);
                    if (candidate instanceof Map) {
                        environment = (Map<String, String>) candidate;
                        break;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (environment == null) {
                return false;
            }
            environment.put(key, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
