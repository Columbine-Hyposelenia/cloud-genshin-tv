package com.cloudgenshin.installer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TARGET_PACKAGE = "com.cloudgenshin.tv";
    private static final String INSTALL_STATUS_ACTION =
            "com.cloudgenshin.installer.action.INSTALL_STATUS";

    private static final String[] APK_URLS = {
            "https://gh-proxy.com/https://github.com/Columbine-Hyposelenia/cloud-genshin-tv/releases/download/v1.0/app-release.apk",
            "https://gh.api.99988866.xyz/https://github.com/Columbine-Hyposelenia/cloud-genshin-tv/releases/download/v1.0/app-release.apk",
            "https://github.com/Columbine-Hyposelenia/cloud-genshin-tv/releases/download/v1.0/app-release.apk",
    };

    private FrameLayout rootLayout;
    private TextView titleText;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView percentText;
    private Button actionButton;

    private ApkDownloader downloader;
    private BroadcastReceiver installStatusReceiver;
    private String downloadedApkPath;
    private boolean installInProgress;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#0B0E16"));
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dpToPx(40), dpToPx(40), dpToPx(40), dpToPx(40));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        rootLayout.addView(content, contentParams);

        titleText = new TextView(this);
        titleText.setText("云原神");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(36);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpToPx(8);
        content.addView(titleText, titleParams);

        TextView subtitleText = new TextView(this);
        subtitleText.setText("电视版云游戏");
        subtitleText.setTextColor(Color.parseColor("#8899AA"));
        subtitleText.setTextSize(16);
        subtitleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = dpToPx(40);
        content.addView(subtitleText, subtitleParams);

        statusText = new TextView(this);
        statusText.setText("正在准备安装...");
        statusText.setTextColor(Color.parseColor("#CCDDDD"));
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dpToPx(20);
        content.addView(statusText, statusParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(8));
        progressParams.bottomMargin = dpToPx(8);
        content.addView(progressBar, progressParams);

        percentText = new TextView(this);
        percentText.setText("0%");
        percentText.setTextColor(Color.parseColor("#8899AA"));
        percentText.setTextSize(14);
        percentText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        percentParams.bottomMargin = dpToPx(30);
        content.addView(percentText, percentParams);

        actionButton = new Button(this);
        actionButton.setText("取消");
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTextSize(16);
        actionButton.setBackgroundColor(Color.parseColor("#2A3A5A"));
        actionButton.setPadding(dpToPx(40), dpToPx(14), dpToPx(40), dpToPx(14));
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleActionButton();
            }
        });
        content.addView(actionButton);

        setContentView(rootLayout);

        registerInstallReceiver();
        startInstallationFlow();
    }

    private void registerInstallReceiver() {
        installStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                handleInstallStatus(status, message);
            }
        };
        IntentFilter filter = new IntentFilter(INSTALL_STATUS_ACTION);
        registerReceiver(installStatusReceiver, filter);
    }

    private void startInstallationFlow() {
        if (isPackageInstalled(TARGET_PACKAGE)) {
            launchTargetApp();
            return;
        }

        if (!isNetworkAvailable()) {
            statusText.setText("网络连接不可用，请检查网络后重试");
            progressBar.setVisibility(View.GONE);
            percentText.setVisibility(View.GONE);
            actionButton.setText("重试");
            actionButton.setVisibility(View.VISIBLE);
            return;
        }

        if (ApkDownloader.isDownloaded(this)) {
            downloadedApkPath = downloader.targetFile().getAbsolutePath();
            installDownloadedApk();
            return;
        }

        downloadApk();
    }

    private void downloadApk() {
        statusText.setText("正在下载云原神...");
        progressBar.setVisibility(View.VISIBLE);
        percentText.setVisibility(View.VISIBLE);
        actionButton.setText("取消");
        actionButton.setVisibility(View.VISIBLE);

        downloader = new ApkDownloader(this);
        downloader.download(APK_URLS, new ApkDownloader.Listener() {
            @Override
            public void onStatus(String status) {
                statusText.setText(status);
            }

            @Override
            public void onProgress(int percent, long downloaded, long total) {
                progressBar.setProgress(percent);
                percentText.setText(percent + "%");
                if (total > 0) {
                    double mbDownloaded = downloaded / (1024.0 * 1024.0);
                    double mbTotal = total / (1024.0 * 1024.0);
                    percentText.setText(percent + "%  ("
                            + String.format("%.1f", mbDownloaded) + "/"
                            + String.format("%.1f", mbTotal) + " MB)");
                }
            }

            @Override
            public void onReady(String apkPath) {
                downloadedApkPath = apkPath;
                progressBar.setProgress(100);
                percentText.setText("100%");
                installDownloadedApk();
            }

            @Override
            public void onError(String error) {
                statusText.setText("下载失败：" + error);
                progressBar.setVisibility(View.GONE);
                percentText.setVisibility(View.GONE);
                actionButton.setText("重试");
                actionButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void installDownloadedApk() {
        if (downloadedApkPath == null) return;
        installInProgress = true;
        statusText.setText("正在安装云原神...");
        progressBar.setIndeterminate(true);
        percentText.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);

        boolean sessionStarted = ApkInstaller.startSessionInstall(
                this, new java.io.File(downloadedApkPath), INSTALL_STATUS_ACTION);

        if (!sessionStarted) {
            ApkInstaller.Result result = ApkInstaller.install(this, downloadedApkPath);
            if (result.success) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkAndLaunch();
                    }
                }, 3000);
            } else {
                statusText.setText("安装失败：" + result.summary);
                progressBar.setIndeterminate(false);
                progressBar.setVisibility(View.GONE);
                actionButton.setText("重试");
                actionButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void handleInstallStatus(int status, String message) {
        installInProgress = false;
        progressBar.setIndeterminate(false);

        if (status == PackageInstaller.STATUS_SUCCESS
                || status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                confirmIntent.setDataAndType(
                        android.net.Uri.fromFile(new java.io.File(downloadedApkPath)),
                        "application/vnd.android.package-archive");
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(confirmIntent);
                } catch (Throwable t) {
                    // fallback
                }
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkAndLaunch();
                }
            }, 2000);
        } else {
            statusText.setText("安装失败：" + ApkInstaller.statusName(status)
                    + (message != null ? " - " + message : ""));
            progressBar.setVisibility(View.GONE);
            actionButton.setText("重试");
            actionButton.setVisibility(View.VISIBLE);
        }
    }

    private void checkAndLaunch() {
        if (isPackageInstalled(TARGET_PACKAGE)) {
            statusText.setText("安装完成！正在启动云原神...");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchTargetApp();
                }
            }, 1000);
        } else {
            statusText.setText("安装未完成，请手动检查");
            actionButton.setText("重试");
            actionButton.setVisibility(View.VISIBLE);
        }
    }

    private void launchTargetApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            statusText.setText("无法启动云原神");
            actionButton.setText("重试");
            actionButton.setVisibility(View.VISIBLE);
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void handleActionButton() {
        if (downloader != null && downloader.targetFile().exists()
                && !installInProgress) {
            downloadedApkPath = downloader.targetFile().getAbsolutePath();
            installDownloadedApk();
        } else if (downloader != null) {
            downloader.cancel();
            statusText.setText("已取消");
            actionButton.setText("重试");
        } else {
            startInstallationFlow();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (downloader != null) {
                downloader.cancel();
            }
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (actionButton.getVisibility() == View.VISIBLE) {
                handleActionButton();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloader != null) {
            downloader.cancel();
        }
        try {
            unregisterReceiver(installStatusReceiver);
        } catch (Throwable ignored) {}
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
