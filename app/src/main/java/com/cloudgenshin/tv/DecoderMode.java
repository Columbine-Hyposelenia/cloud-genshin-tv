package com.cloudgenshin.tv;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class DecoderMode {
    public static final int OUTPUT_TEXTURE = 0;
    public static final int OUTPUT_BYTEBUFFER = 1;

    private static final String KEY_OUTPUT = "output";
    private static final String KEY_HW = "hw";
    private static final String FILE_NAME = "decoder_mode.properties";

    private static volatile int sOutput = OUTPUT_BYTEBUFFER;
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
                sOutput = Integer.parseInt(props.getProperty(KEY_OUTPUT, String.valueOf(OUTPUT_BYTEBUFFER)));
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

    public static int output() {
        return sOutput;
    }

    public static boolean isByteBuffer() {
        return sOutput == OUTPUT_BYTEBUFFER;
    }

    public static boolean hardwareDecode() {
        return sHw;
    }

    public static synchronized void setOutput(Context context, int value) {
        sOutput = value;
        persist(context);
    }

    public static synchronized void setHardwareDecode(Context context, boolean value) {
        sHw = value;
        persist(context);
    }

    private static void persist(Context context) {
        Properties props = new Properties();
        props.setProperty(KEY_OUTPUT, String.valueOf(sOutput));
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
