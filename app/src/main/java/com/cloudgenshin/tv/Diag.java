package com.cloudgenshin.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class Diag {
    private static final String PREFS_NAME = "diag";
    private static final String KEY_VERBOSE = "verbose";

    private static volatile JSONObject sTelemetry;
    private static volatile long sTelemetryAt;

    private Diag() {
    }

    public static boolean isVerbose(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_VERBOSE, false);
    }

    public static void setVerbose(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_VERBOSE, value).apply();
    }

    public static void setTelemetry(JSONObject telemetry) {
        sTelemetry = telemetry;
        sTelemetryAt = System.currentTimeMillis();
    }

    public static JSONObject telemetry() {
        return sTelemetry;
    }

    public static long telemetryAgeMillis() {
        if (sTelemetry == null) {
            return -1;
        }
        return System.currentTimeMillis() - sTelemetryAt;
    }
}
