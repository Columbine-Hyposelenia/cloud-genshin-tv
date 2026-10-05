package com.cloudgenshin.tv;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public final class AppLog {
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final String DIR_NAME = "logs";
    private static final String LIVE_NAME = "live.log";
    private static final String OLD_NAME = "live.log.1";

    private static volatile AppLog sInstance;

    private final File mDir;
    private final File mLive;
    private final File mOld;
    private Process mProcess;
    private Thread mThread;
    private volatile boolean mRunning;

    private AppLog(Context context) {
        mDir = new File(context.getFilesDir(), DIR_NAME);
        mLive = new File(mDir, LIVE_NAME);
        mOld = new File(mDir, OLD_NAME);
    }

    public static synchronized void start(Context context) {
        if (sInstance == null) {
            sInstance = new AppLog(context.getApplicationContext());
        }
        sInstance.begin();
    }

    public static AppLog get() {
        return sInstance;
    }

    private void begin() {
        if (mRunning) {
            return;
        }
        if (!mDir.exists()) {
            mDir.mkdirs();
        }
        try {
            mProcess = Runtime.getRuntime().exec(new String[] {"logcat", "-v", "threadtime"});
        } catch (Exception e) {
            return;
        }
        mRunning = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pump();
            }
        }, "app-log");
        mThread.start();
    }

    private void pump() {
        FileOutputStream out = null;
        long written = 0L;
        try {
            out = new FileOutputStream(mLive, true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    mProcess.getInputStream()));
            String line;
            while (mRunning && (line = reader.readLine()) != null) {
                byte[] data = (line + '\n').getBytes("UTF-8");
                out.write(data);
                written += data.length;
                if (written >= MAX_BYTES) {
                    out.flush();
                    out.close();
                    rotate();
                    out = new FileOutputStream(mLive, true);
                    written = 0L;
                }
            }
        } catch (Exception e) {
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void rotate() {
        try {
            if (mOld.exists()) {
                mOld.delete();
            }
            if (mLive.exists()) {
                mLive.renameTo(mOld);
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    public File liveFile() {
        return mLive;
    }

    public File oldFile() {
        return mOld;
    }

    public void clear() {
        try {
            Runtime.getRuntime().exec(new String[] {"logcat", "-c"}).waitFor();
        } catch (Exception ignored) {
        }
        try {
            if (mLive.exists()) {
                new FileOutputStream(mLive, false).close();
            }
            if (mOld.exists()) {
                mOld.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public String readFile(File file, int maxChars) {
        if (file == null || !file.exists()) {
            return "";
        }
        FileInputStream in = null;
        try {
            int length = (int) file.length();
            byte[] all = new byte[length];
            in = new FileInputStream(file);
            int offset = 0;
            int read;
            while (offset < length && (read = in.read(all, offset, length - offset)) > 0) {
                offset += read;
            }
            String content = new String(all, 0, offset, "UTF-8");
            if (maxChars > 0 && content.length() > maxChars) {
                content = content.substring(content.length() - maxChars);
            }
            return content;
        } catch (Exception e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String snapshot() {
        return execCapture(new String[] {"logcat", "-d", "-v", "threadtime"});
    }

    public static String bufferInfo() {
        return execCapture(new String[] {"logcat", "-g"});
    }

    private static String execCapture(String[] command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            final Process running = process;
            final StringBuilder error = new StringBuilder();
            Thread errorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            running.getErrorStream()));
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            synchronized (error) {
                                error.append(line).append('\n');
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            errorThread.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            int exit = process.waitFor();
            errorThread.join(500);
            synchronized (error) {
                if (error.length() > 0) {
                    builder.append("\n[stderr]\n").append(error);
                }
            }
            if (builder.length() == 0) {
                builder.append("[no output, exit ").append(exit).append(']');
            }
            return builder.toString();
        } catch (Exception e) {
            return "[exec failed] " + e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
