package com.cloudgenshin.installer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String RAW =
            "https://raw.githubusercontent.com/Columbine-Hyposelenia/cloud-genshin-tv/master/dist/cloud-genshin.apk";

    private static final String[] MIRRORS = {
            "https://gh-proxy.com/" + RAW,
            "https://litter.catbox.moe/bw53cj.apk",
            "https://ghproxy.net/" + RAW,
            "https://gh.ddlc.top/" + RAW,
            "https://cors.isteed.cc/" + RAW,
            RAW,
    };

    private TextView mStatus;
    private ProgressBar mProgress;
    private ApkDownloader mDownloader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int padding = dp(48);
        container.setPadding(padding, padding, padding, padding);
        container.setBackgroundColor(Color.rgb(9, 13, 26));

        TextView title = new TextView(this);
        title.setText("云·原神");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        container.addView(title);

        mStatus = new TextView(this);
        mStatus.setText("preparing...");
        mStatus.setTextColor(Color.rgb(180, 198, 228));
        mStatus.setTextSize(16);
        mStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(28);
        mStatus.setLayoutParams(statusParams);
        container.addView(mStatus);

        mProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mProgress.setMax(100);
        mProgress.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = dp(18);
        mProgress.setLayoutParams(progressParams);
        container.addView(mProgress);

        setContentView(container);
        startDownload();
    }

    private void startDownload() {
        mDownloader = new ApkDownloader(this);
        mDownloader.download(MIRRORS, new ApkDownloader.Listener() {
            @Override
            public void onStatus(String status) {
                mStatus.setText(status);
            }

            @Override
            public void onProgress(int percent) {
                mProgress.setProgress(percent);
                mStatus.setText(percent + "%");
            }

            @Override
            public void onReady(String apkPath) {
                mStatus.setText("installing...");
                install(apkPath);
            }

            @Override
            public void onError(String error) {
                mStatus.setText("download failed: " + error);
            }
        });
    }

    private void install(String apkPath) {
        java.io.File file = new java.io.File(apkPath);
        boolean sessionStarted = ApkInstaller.startSession(this, file);
        if (!sessionStarted) {
            String result = ApkInstaller.install(this, apkPath);
            mStatus.setText(result);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
