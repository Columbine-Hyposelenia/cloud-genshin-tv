package com.cloudgenshin.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ApkDownloader {

    public interface Listener {
        void onStatus(String status);
        void onProgress(int percent, long downloaded, long total);
        void onReady(String apkPath);
        void onError(String error);
    }

    private static final String DOWNLOAD_DIR = "downloads";
    private static final String APK_FILE = "cloudgenshin.apk";
    private static final int MAX_ATTEMPTS_PER_URL = 2;
    private static final long RETRY_DELAY_MS = 2000;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread workerThread;
    private volatile boolean cancelled;

    public ApkDownloader(Context context) {
        this.context = context;
    }

    public void download(final String[] urls, final Listener listener) {
        if (urls == null || urls.length == 0) {
            postError(listener, "没有可用的下载地址");
            return;
        }
        cancelled = false;
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String lastError = "未知错误";
                for (int mi = 0; mi < urls.length && !cancelled; mi++) {
                    String url = urls[mi];
                    String mirrorLabel = urls.length > 1
                            ? ("镜像 " + (mi + 1) + "/" + urls.length) : "";
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_URL && !cancelled; attempt++) {
                        String err = attemptDownload(url, listener, attempt, mirrorLabel);
                        if (err == null) return;
                        lastError = err;
                        if (cancelled) return;
                        if (attempt < MAX_ATTEMPTS_PER_URL) {
                            postStatus(listener, (mirrorLabel.length() > 0 ? mirrorLabel + "  " : "")
                                    + "重试 (" + attempt + "/" + (MAX_ATTEMPTS_PER_URL - 1)
                                    + ")...  " + err);
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                return;
                            }
                        }
                    }
                    if (mi < urls.length - 1 && !cancelled) {
                        postStatus(listener, "当前镜像不可用，切换下一个镜像...");
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
                postError(listener, "所有镜像均下载失败：" + lastError
                        + "。请检查网络连接后重试。");
            }
        }, "apk-download");
        workerThread.start();
    }

    private String attemptDownload(String urlStr, Listener listener, int attempt, String mirrorLabel) {
        HttpURLConnection conn = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            String prefix = (mirrorLabel != null && mirrorLabel.length() > 0) ? mirrorLabel + "  " : "";
            postStatus(listener, prefix + (attempt == 1 ? "正在连接..." : "正在重试连接..."));

            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (target.exists()) target.delete();

            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.0.0 Safari/537.36");

            if (conn instanceof HttpsURLConnection) {
                applyTLS12((HttpsURLConnection) conn);
            }

            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return "HTTP " + code;
            }

            long total = conn.getContentLength();
            String totalHeader = conn.getHeaderField("Content-Length");
            if (total <= 0 && totalHeader != null) {
                try {
                    total = Long.parseLong(totalHeader);
                } catch (Throwable ignored) {}
            }

            input = conn.getInputStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[65536];
            long downloaded = 0;
            int lastPercent = -1;
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (cancelled) {
                    output.close();
                    if (target.exists()) target.delete();
                    return "已取消";
                }
                output.write(buffer, 0, read);
                downloaded += read;
                int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    postProgress(listener, percent, downloaded, total);
                }
            }
            output.flush();
            output.close();
            input.close();

            long actual = target.length();
            if (actual < 1024 * 1024) {
                return "文件过小 (" + actual + " bytes)，可能下载不完整";
            }
            if (total > 0 && actual != total) {
                return "下载不完整：期望 " + total + " bytes，实际 " + actual + " bytes";
            }
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(target);
                int b0 = fis.read();
                int b1 = fis.read();
                int b2 = fis.read();
                int b3 = fis.read();
                fis.close();
                if (b0 != 0x50 || b1 != 0x4B || b2 != 0x03 || b3 != 0x04) {
                    return "文件不是有效的 APK（ZIP 头校验失败）";
                }
            } catch (Throwable t) {
                return "文件校验失败：" + t.getMessage();
            }
            postReady(listener, target.getAbsolutePath());
            return null;
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.length() == 0) msg = t.getClass().getSimpleName();
            return msg;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static void applyTLS12(HttpsURLConnection conn) {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, null, null);
            conn.setSSLSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
        } catch (NoSuchAlgorithmException e) {
            // leave default
        } catch (KeyManagementException e) {
            // leave default
        }
    }

    private static class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

        Tls12SocketFactory(SSLSocketFactory base) {
            this.delegate = base;
        }

        private SSLSocket patch(SSLSocket s) {
            try {
                s.setEnabledProtocols(PROTOCOLS);
            } catch (Throwable ignored) {}
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override public java.net.Socket createSocket() throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket());
        }
        @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket(s, host, port, autoClose));
        }
        @Override public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket(host, port));
        }
        @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket(host, port, localHost, localPort));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket(host, port));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
            return patch((SSLSocket) delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    public void cancel() {
        cancelled = true;
        if (workerThread != null) workerThread.interrupt();
    }

    public File targetFile() {
        File dir = context.getExternalFilesDir(DOWNLOAD_DIR);
        if (dir == null) {
            dir = new File(context.getFilesDir(), DOWNLOAD_DIR);
        }
        return new File(dir, APK_FILE);
    }

    public static boolean isDownloaded(Context context) {
        File f = new ApkDownloader(context).targetFile();
        return f.exists() && f.length() > 1024 * 1024;
    }

    private void postStatus(final Listener l, final String s) {
        mainHandler.post(new Runnable() {
            @Override public void run() { l.onStatus(s); }
        });
    }

    private void postProgress(final Listener l, final int p, final long d, final long t) {
        mainHandler.post(new Runnable() {
            @Override public void run() { l.onProgress(p, d, t); }
        });
    }

    private void postReady(final Listener l, final String path) {
        mainHandler.post(new Runnable() {
            @Override public void run() { l.onReady(path); }
        });
    }

    private void postError(final Listener l, final String e) {
        mainHandler.post(new Runnable() {
            @Override public void run() { l.onError(e); }
        });
    }
}
