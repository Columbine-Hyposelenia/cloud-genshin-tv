package com.cloudgenshin.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
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

        void onProgress(int percent);

        void onReady(String apkPath);

        void onError(String error);
    }

    private static final String DIR = "app";
    private static final String FILE = "cloud-genshin.apk";
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY = 2000L;

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private Thread mWorker;
    private volatile boolean mCancelled;

    public ApkDownloader(Context context) {
        mContext = context;
    }

    public void download(final String[] urls, final Listener listener) {
        if (urls == null || urls.length == 0) {
            postError(listener, "no download source");
            return;
        }
        mCancelled = false;
        mWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                String lastError = "unknown error";
                for (int mirror = 0; mirror < urls.length && !mCancelled; mirror++) {
                    String label = "mirror " + (mirror + 1) + "/" + urls.length;
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS && !mCancelled; attempt++) {
                        String error = attemptDownload(urls[mirror], listener, attempt, label);
                        if (error == null) {
                            return;
                        }
                        lastError = error;
                        if (attempt < MAX_ATTEMPTS) {
                            postStatus(listener, label + " retry... " + error);
                            sleep(RETRY_DELAY);
                        }
                    }
                    if (mirror < urls.length - 1 && !mCancelled) {
                        postStatus(listener, "switching mirror...");
                        sleep(800L);
                    }
                }
                postError(listener, "all mirrors failed: " + lastError);
            }
        }, "apk-download");
        mWorker.start();
    }

    private String attemptDownload(String url, Listener listener, int attempt, String label) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            postStatus(listener, label + (attempt == 1 ? " connecting..." : " reconnecting..."));
            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (target.exists()) {
                target.delete();
            }

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 5.1) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/39.0.0.0 Safari/537.36");
            if (connection instanceof HttpsURLConnection) {
                applyTls12((HttpsURLConnection) connection);
            }
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return "HTTP " + code;
            }

            long total = connection.getContentLength();
            String lengthHeader = connection.getHeaderField("Content-Length");
            if (total <= 0 && lengthHeader != null) {
                try {
                    total = Long.parseLong(lengthHeader);
                } catch (Throwable ignored) {
                }
            }

            input = connection.getInputStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[65536];
            long downloaded = 0L;
            int lastPercent = -1;
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (mCancelled) {
                    output.close();
                    if (target.exists()) {
                        target.delete();
                    }
                    return "cancelled";
                }
                output.write(buffer, 0, read);
                downloaded += read;
                int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    postProgress(listener, percent);
                }
            }
            output.flush();
            output.close();
            input.close();

            long actual = target.length();
            if (actual < 1024 * 1024) {
                return "file too small (" + actual + ")";
            }
            if (total > 0 && actual != total) {
                return "incomplete: expected " + total + " got " + actual;
            }
            if (!hasZipMagic(target)) {
                return "not a valid apk";
            }
            postReady(listener, target.getAbsolutePath());
            return null;
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null || message.length() == 0) {
                message = throwable.getClass().getSimpleName();
            }
            return message;
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Throwable ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean hasZipMagic(File file) {
        java.io.FileInputStream stream = null;
        try {
            stream = new java.io.FileInputStream(file);
            int b0 = stream.read();
            int b1 = stream.read();
            int b2 = stream.read();
            int b3 = stream.read();
            return b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04;
        } catch (Throwable throwable) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void applyTls12(HttpsURLConnection connection) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            connection.setSSLSocketFactory(new Tls12SocketFactory(context.getSocketFactory()));
        } catch (NoSuchAlgorithmException ignored) {
        } catch (KeyManagementException ignored) {
        }
    }

    private static class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory mDelegate;
        private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

        Tls12SocketFactory(SSLSocketFactory factory) {
            mDelegate = factory;
        }

        private SSLSocket patch(SSLSocket socket) {
            try {
                socket.setEnabledProtocols(PROTOCOLS);
            } catch (Throwable ignored) {
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return mDelegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return mDelegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return patch((SSLSocket) mDelegate.createSocket());
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            return patch((SSLSocket) mDelegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch((SSLSocket) mDelegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress localHost,
                int localPort) throws IOException {
            return patch((SSLSocket) mDelegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            return patch((SSLSocket) mDelegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port,
                java.net.InetAddress localHost, int localPort) throws IOException {
            return patch((SSLSocket) mDelegate.createSocket(host, port, localHost, localPort));
        }
    }

    public File targetFile() {
        File dir = mContext.getExternalFilesDir(DIR);
        if (dir == null) {
            dir = new File(mContext.getFilesDir(), DIR);
        }
        return new File(dir, FILE);
    }

    public void cancel() {
        mCancelled = true;
        if (mWorker != null) {
            mWorker.interrupt();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
        }
    }

    private void postStatus(final Listener listener, final String status) {
        mMain.post(new Runnable() {
            @Override
            public void run() {
                listener.onStatus(status);
            }
        });
    }

    private void postProgress(final Listener listener, final int percent) {
        mMain.post(new Runnable() {
            @Override
            public void run() {
                listener.onProgress(percent);
            }
        });
    }

    private void postReady(final Listener listener, final String path) {
        mMain.post(new Runnable() {
            @Override
            public void run() {
                listener.onReady(path);
            }
        });
    }

    private void postError(final Listener listener, final String error) {
        mMain.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(error);
            }
        });
    }
}
