package com.cloudgenshin.tv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

public final class LogServer {
    public interface Source {
        String report();

        String liveLog();
    }

    private static final int[] PORTS = {8080, 8081, 8082, 8083};

    private static LogServer sInstance;

    private final Source mSource;
    private ServerSocket mSocket;
    private Thread mThread;
    private volatile boolean mRunning;
    private int mPort;

    private LogServer(Source source) {
        mSource = source;
    }

    public static synchronized LogServer get(Source source) {
        if (sInstance == null) {
            sInstance = new LogServer(source);
        }
        return sInstance;
    }

    public boolean start() {
        if (mRunning) {
            return true;
        }
        for (int port : PORTS) {
            try {
                mSocket = new ServerSocket(port);
                mPort = port;
                break;
            } catch (Exception e) {
                mSocket = null;
            }
        }
        if (mSocket == null) {
            return false;
        }
        try {
            mSocket.setSoTimeout(500);
        } catch (Exception ignored) {
        }
        mRunning = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "log-server");
        mThread.start();
        return true;
    }

    public void stop() {
        mRunning = false;
        ServerSocket socket = mSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
        mSocket = null;
    }

    public boolean isRunning() {
        return mRunning;
    }

    public String address() {
        String ip = lanIp();
        if (ip == null) {
            return "";
        }
        return "http://" + ip + ":" + mPort + "/";
    }

    private void loop() {
        while (mRunning) {
            Socket client;
            try {
                client = mSocket.accept();
            } catch (Exception e) {
                continue;
            }
            try {
                handle(client);
            } catch (Exception ignored) {
            } finally {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void handle(Socket client) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                client.getInputStream()));
        String requestLine = reader.readLine();
        String path = "/";
        if (requestLine != null) {
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) {
                path = parts[1];
            }
        }
        String headerLine;
        while ((headerLine = reader.readLine()) != null && headerLine.length() > 0) {
        }

        String body = route(path);
        byte[] data = body.getBytes("UTF-8");
        OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.0 200 OK\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    private String route(String path) {
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.startsWith("/logcat")) {
            return AppLog.snapshot();
        }
        if (path.startsWith("/log")) {
            return mSource.liveLog();
        }
        return mSource.report();
    }

    private static String lanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.isSiteLocalAddress()
                            && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
}
