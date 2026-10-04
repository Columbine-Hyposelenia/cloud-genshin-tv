package com.cloudgenshin.installer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ApkInstaller {

    private static final String PUBLIC_NAME = "CloudGenshin.apk";
    private static final String MIME = "application/vnd.android.package-archive";
    private static final String ENTRY = "base.apk";

    private static final String[] HELPERS = {
            "com.tcl.tvweishi",
            "com.tcl.securityapp",
            "tv.huan.tvhelper",
            "com.tcl.tvappmanager",
            "com.tcl.ui_mediaCenter",
            "com.tcl.mediacenter",
            "com.tcl.common.viewer",
    };

    private ApkInstaller() {
    }

    public static boolean startSession(Activity activity, File apk) {
        PackageInstaller installer = obtain(activity);
        if (installer == null) {
            return false;
        }
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            FileInputStream in = new FileInputStream(apk);
            OutputStream out = session.openWrite(ENTRY, 0, apk.length());
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            session.fsync(out);
            in.close();
            out.close();

            Intent status = new Intent(activity, InstallResultReceiver.class);
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= android.app.PendingIntent.FLAG_MUTABLE;
            }
            android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(
                    activity, sessionId, status, flags);
            session.commit(pending.getIntentSender());
            return true;
        } catch (Throwable throwable) {
            return false;
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static PackageInstaller obtain(Activity activity) {
        Object service = activity.getSystemService("package_installer");
        if (service instanceof PackageInstaller) {
            return (PackageInstaller) service;
        }
        return null;
    }

    public static String install(Activity activity, String privatePath) {
        File source = new File(privatePath);
        if (!source.exists()) {
            return "apk missing";
        }

        File publicFile = publish(source);

        List<Uri> uris = new ArrayList<Uri>();
        if (publicFile != null) {
            uris.add(Uri.fromFile(publicFile));
        }
        uris.add(Uri.fromFile(source));

        for (Uri uri : uris) {
            if (launchInstallActivity(activity, uri)) {
                return "installer launched";
            }
        }

        if (publicFile != null && framework(activity, Uri.fromFile(publicFile))) {
            return "framework install invoked";
        }

        File rootTarget = publicFile != null ? publicFile : source;
        if (root(rootTarget)) {
            return "root install succeeded";
        }

        String helper = launchHelper(activity);
        if (helper != null) {
            return "helper opened: " + helper;
        }
        return "no install channel available";
    }

    private static boolean launchInstallActivity(Activity activity, Uri uri) {
        PackageManager pm = activity.getPackageManager();
        List<ComponentName> components = new ArrayList<ComponentName>();
        collect(pm, Intent.ACTION_INSTALL_PACKAGE, uri, components);
        collect(pm, Intent.ACTION_VIEW, uri, components);

        for (ComponentName component : components) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, MIME);
                intent.setComponent(component);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                activity.startActivity(intent);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void collect(PackageManager pm, String action, Uri uri,
            List<ComponentName> out) {
        try {
            Intent intent = new Intent(action);
            intent.setDataAndType(uri, MIME);
            List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
            if (resolved == null) {
                return;
            }
            for (ResolveInfo info : resolved) {
                if (info.activityInfo == null) {
                    continue;
                }
                ComponentName component = new ComponentName(
                        info.activityInfo.packageName, info.activityInfo.name);
                if (!out.contains(component)) {
                    out.add(component);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean framework(Activity activity, Uri uri) {
        try {
            PackageManager pm = activity.getPackageManager();
            Class<?> observer = Class.forName("android.content.pm.IPackageInstallObserver");
            java.lang.reflect.Method method = PackageManager.class.getMethod("installPackage",
                    Uri.class, observer, int.class, String.class);
            method.invoke(pm, uri, null, 0, activity.getPackageName());
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean root(File apk) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("su");
            builder.redirectErrorStream(true);
            process = builder.start();
            OutputStream out = process.getOutputStream();
            String script = "setprop dalvik.vm.dex2oat-filter interpret-only\n"
                    + "pm install -r " + apk.getAbsolutePath() + "\nexit\n";
            out.write(script.getBytes());
            out.flush();
            out.close();

            InputStream in = process.getInputStream();
            StringBuilder result = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                result.append(new String(buffer, 0, read));
            }
            process.waitFor();
            return result.toString().contains("Success");
        } catch (Throwable throwable) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String launchHelper(Activity activity) {
        for (String pkg : HELPERS) {
            try {
                Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent == null) {
                    continue;
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return pkg;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static File publish(File source) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File target = new File(dir, PUBLIC_NAME);
            if (target.exists()) {
                target.delete();
            }
            if (!copy(source, target)) {
                return null;
            }
            target.setReadable(true, false);
            return target;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean copy(File source, File target) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (Throwable throwable) {
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
