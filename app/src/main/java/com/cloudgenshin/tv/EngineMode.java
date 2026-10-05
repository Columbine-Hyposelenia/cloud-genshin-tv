package com.cloudgenshin.tv;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class EngineMode {
    public static final int BACKEND_TEXTURE = 0;
    public static final int BACKEND_SURFACE = 1;

    public static final int GRAPHICS_HARDWARE = 0;
    public static final int GRAPHICS_SOFTWARE = 1;
    public static final int GRAPHICS_NO_COMPOSITOR = 2;

    private static final String KEY_BACKEND = "backend";
    private static final String KEY_GRAPHICS = "graphics";
    private static final String FILE_NAME = "engine_mode.properties";

    private static volatile int sBackend = BACKEND_TEXTURE;
    private static volatile int sGraphics = GRAPHICS_HARDWARE;
    private static volatile boolean sLoaded = false;

    private EngineMode() {
    }

    public static synchronized void init(Context context) {
        if (sLoaded) {
            return;
        }
        Properties props = new Properties();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                props.load(in);
                sBackend = clampBackend(Integer.parseInt(props.getProperty(
                        KEY_BACKEND, String.valueOf(BACKEND_TEXTURE))));
                sGraphics = clampGraphics(Integer.parseInt(props.getProperty(
                        KEY_GRAPHICS, String.valueOf(GRAPHICS_HARDWARE))));
            } catch (Exception ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        sLoaded = true;
    }

    public static int backend() {
        return sBackend;
    }

    public static int graphics() {
        return sGraphics;
    }

    public static synchronized void setBackend(Context context, int value) {
        sBackend = clampBackend(value);
        persist(context);
    }

    public static synchronized void setGraphics(Context context, int value) {
        sGraphics = clampGraphics(value);
        persist(context);
    }

    private static int clampBackend(int value) {
        return value == BACKEND_SURFACE ? BACKEND_SURFACE : BACKEND_TEXTURE;
    }

    private static int clampGraphics(int value) {
        if (value == GRAPHICS_SOFTWARE || value == GRAPHICS_NO_COMPOSITOR) {
            return value;
        }
        return GRAPHICS_HARDWARE;
    }

    private static void persist(Context context) {
        Properties props = new Properties();
        props.setProperty(KEY_BACKEND, String.valueOf(sBackend));
        props.setProperty(KEY_GRAPHICS, String.valueOf(sGraphics));
        File file = new File(context.getFilesDir(), FILE_NAME);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, null);
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
