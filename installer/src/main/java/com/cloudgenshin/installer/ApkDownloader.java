package com.cloudgenshin.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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

    private static final String UA = "Mozilla/5.0 (Linux; Android 5.1) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/39.0.0.0 Safari/537.36";

    private static final int PROBE_BYTES = 512 * 1024;
    private static final int PROBE_TIMEOUT_MS = 8000;
    private static final long MIN_PROBE_BYTES = 96L * 1024;
    private static final int SEGMENTS = 4;
    private static final long MIN_SEGMENTED_TOTAL = 8L * 1024 * 1024;
    private static final long MIN_FILE = 1L * 1024 * 1024;

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private Thread mWorker;
    private volatile boolean mCancelled;
    private volatile long mDownloaded;
    private volatile long mTotal;
    private volatile int mLastPercent = -1;

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
                String[] ranked = rankBySpeed(urls, listener);
                String lastError = "unknown error";
                for (int i = 0; i < ranked.length && !mCancelled; i++) {
                    String label = "mirror " + (i + 1) + "/" + ranked.length;
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS && !mCancelled; attempt++) {
                        String error = attemptDownload(ranked[i], listener, attempt, label);
                        if (error == null) {
                            return;
                        }
                        lastError = error;
                        if (attempt < MAX_ATTEMPTS) {
                            postStatus(listener, label + " retry... " + error);
                            sleep(RETRY_DELAY);
                        }
                    }
                    if (i < ranked.length - 1 && !mCancelled) {
                        postStatus(listener, "switching mirror...");
                        sleep(800L);
                    }
                }
                postError(listener, "all mirrors failed: " + lastError);
            }
        }, "apk-download");
        mWorker.start();
    }

    private String[] rankBySpeed(String[] urls, Listener listener) {
        if (urls.length <= 1) {
            return urls;
        }
        postStatus(listener, "measuring mirror speed...");
        final long[] speed = new long[urls.length];
        Thread[] threads = new Thread[urls.length];
        for (int i = 0; i < urls.length; i++) {
            final int index = i;
            final String url = urls[i];
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    speed[index] = probeSpeed(url);
                }
            }, "probe-" + i);
            threads[i].start();
        }
        long deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS + 4000L;
        for (int i = 0; i < threads.length; i++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining < 1) {
                remaining = 1;
            }
            try {
                threads[i].join(remaining);
            } catch (InterruptedException ignored) {
            }
            if (threads[i].isAlive()) {
                threads[i].interrupt();
            }
        }
        Integer[] order = new Integer[urls.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        for (int i = 0; i < order.length; i++) {
            for (int j = i + 1; j < order.length; j++) {
                if (speed[order[j]] > speed[order[i]]) {
                    Integer tmp = order[i];
                    order[i] = order[j];
                    order[j] = tmp;
                }
            }
        }
        String[] ranked = new String[urls.length];
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            ranked[i] = urls[order[i]];
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append(order[i] + 1).append(':');
            summary.append(speed[order[i]] > 0 ? kbps(speed[order[i]]) : "fail");
        }
        postStatus(listener, "speed " + summary);
        return ranked;
    }

    private static String kbps(long bytesPerSecond) {
        return (bytesPerSecond / 1024) + "kB/s";
    }

    private long probeSpeed(String url) {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
            connection.setRequestProperty("User-Agent", UA);
            if (connection instanceof HttpsURLConnection) {
                applyTls12((HttpsURLConnection) connection);
            }
            connection.connect();
            int code = connection.getResponseCode();
            if (code != 200 && code != 206) {
                return -1;
            }
            input = connection.getInputStream();
            byte[] buffer = new byte[16384];
            long start = System.currentTimeMillis();
            long got = 0;
            int read;
            while (got < PROBE_BYTES && !mCancelled) {
                read = input.read(buffer);
                if (read <= 0) {
                    break;
                }
                if (got == 0 && (buffer[0] & 0xFF) != 0x50) {
                    return -1;
                }
                got += read;
                if (System.currentTimeMillis() - start > PROBE_TIMEOUT_MS) {
                    break;
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            if (got < MIN_PROBE_BYTES) {
                return -1;
            }
            if (elapsed < 1) {
                elapsed = 1;
            }
            return got * 1000L / elapsed;
        } catch (Throwable throwable) {
            return -1;
        } finally {
            closeQuietly(input);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String attemptDownload(String url, Listener listener, int attempt, String label) {
        postStatus(listener, label + (attempt == 1 ? " connecting..." : " reconnecting..."));
        String segmented = attemptSegmented(url, listener, label);
        if (segmented == null) {
            return null;
        }
        if (mCancelled) {
            return "cancelled";
        }
        postStatus(listener, label + ": parallel failed (" + segmented + "), retrying plain...");
        return attemptPlain(url, listener, label);
    }

    private String attemptSegmented(String url, Listener listener, String label) {
        HttpURLConnection connection = null;
        InputStream input = null;
        RandomAccessFile raf = null;
        try {
            connection = open(url);
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.connect();
            if (connection.getResponseCode() != 206) {
                return "no range support";
            }
            input = connection.getInputStream();
            input.read();
            String contentRange = connection.getHeaderField("Content-Range");
            long total = parseTotal(contentRange);
            closeQuietly(input);
            input = null;
            connection.disconnect();
            connection = null;
            if (total < MIN_SEGMENTED_TOTAL) {
                return "size unknown";
            }

            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (target.exists()) {
                target.delete();
            }
            raf = new RandomAccessFile(target, "rw");
            raf.setLength(total);

            mDownloaded = 0L;
            mTotal = total;
            mLastPercent = -1;
            postProgress(listener, 0);

            long chunk = total / SEGMENTS;
            Thread[] threads = new Thread[SEGMENTS];
            String[] errors = new String[SEGMENTS];
            for (int i = 0; i < SEGMENTS; i++) {
                long start = i * chunk;
                long end = (i == SEGMENTS - 1) ? total - 1 : (start + chunk - 1);
                final long segStart = start;
                final long segEnd = end;
                final String segUrl = url;
                final String[] segErrors = errors;
                final int segIndex = i;
                threads[i] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String result = downloadSegment(segUrl, segStart, segEnd, listener);
                        segErrors[segIndex] = result;
                    }
                }, "seg-" + i);
                threads[i].start();
            }
            for (int i = 0; i < SEGMENTS; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException interrupted) {
                    mCancelled = true;
                }
            }
            for (int i = 0; i < SEGMENTS; i++) {
                if (errors[i] != null) {
                    return errors[i];
                }
            }
            raf.close();
            raf = null;

            long actual = target.length();
            if (actual < MIN_FILE) {
                return "file too small (" + actual + ")";
            }
            if (actual != total) {
                return "incomplete: expected " + total + " got " + actual;
            }
            if (!hasZipMagic(target)) {
                return "not a valid apk";
            }
            postProgress(listener, 100);
            postReady(listener, target.getAbsolutePath());
            return null;
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null || message.length() == 0) {
                message = throwable.getClass().getSimpleName();
            }
            return message;
        } finally {
            closeQuietly(input);
            if (connection != null) {
                connection.disconnect();
            }
            if (raf != null) {
                try {
                    raf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private String downloadSegment(String url, long start, long end, Listener listener) {
        HttpURLConnection connection = null;
        InputStream input = null;
        RandomAccessFile raf = null;
        try {
            connection = open(url);
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
            connection.connect();
            int code = connection.getResponseCode();
            if (code != 206) {
                return "range rejected: HTTP " + code;
            }
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange == null || !contentRange.startsWith("bytes " + start + "-")) {
                return "range mismatch";
            }
            input = connection.getInputStream();
            raf = new RandomAccessFile(targetFile(), "rw");
            FileChannel channel = raf.getChannel();
            byte[] buffer = new byte[65536];
            long position = start;
            int read;
            while (position <= end && !mCancelled) {
                read = input.read(buffer);
                if (read <= 0) {
                    break;
                }
                long allow = end - position + 1;
                int length = read;
                if (length > allow) {
                    length = (int) allow;
                }
                ByteBuffer slice = ByteBuffer.wrap(buffer, 0, length);
                while (slice.hasRemaining()) {
                    position += channel.write(slice, position);
                }
                mDownloaded += length;
                publishProgress(listener);
            }
            channel.close();
            raf.close();
            raf = null;
            if (mCancelled) {
                return "cancelled";
            }
            if (position <= end) {
                return "segment truncated at " + position;
            }
            return null;
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null || message.length() == 0) {
                message = throwable.getClass().getSimpleName();
            }
            return message;
        } finally {
            closeQuietly(input);
            if (connection != null) {
                connection.disconnect();
            }
            if (raf != null) {
                try {
                    raf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void publishProgress(Listener listener) {
        long total = mTotal;
        if (total <= 0) {
            return;
        }
        int percent = (int) (mDownloaded * 100L / total);
        if (percent > 99) {
            percent = 99;
        }
        if (percent != mLastPercent) {
            mLastPercent = percent;
            postProgress(listener, percent);
        }
    }

    private String attemptPlain(String url, Listener listener, String label) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            postStatus(listener, label + " plain download...");
            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (target.exists()) {
                target.delete();
            }

            connection = open(url);
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
            if (actual < MIN_FILE) {
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
            closeQuietly(input);
            if (connection != null) {
                connection.disconnect();
            }
            closeQuietly(output);
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (connection instanceof HttpsURLConnection) {
            applyTls12((HttpsURLConnection) connection);
        }
        return connection;
    }

    private long parseTotal(String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0) {
            return -1;
        }
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (Throwable throwable) {
            return -1;
        }
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
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
            closeQuietly(stream);
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
