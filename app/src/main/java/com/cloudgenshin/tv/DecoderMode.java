package com.cloudgenshin.tv;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class DecoderMode {
    private static final String KEY_HW = "hw";
    private static final String FILE_NAME = "decoder_mode.properties";

    private static volatile boolean sHw = true;
    private static volatile boolean sLoaded = false;

    private DecoderMode() {
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
                sHw = Boolean.parseBoolean(props.getProperty(KEY_HW, "true"));
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

    public static boolean hardwareDecode() {
        return sHw;
    }

    public static synchronized void setHardwareDecode(Context context, boolean value) {
        sHw = value;
        persist(context);
    }

    private static void persist(Context context) {
        Properties props = new Properties();
        props.setProperty(KEY_HW, String.valueOf(sHw));
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
