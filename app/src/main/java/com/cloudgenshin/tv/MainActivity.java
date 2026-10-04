package com.cloudgenshin.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.Callback;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.MediaSession;
import org.mozilla.geckoview.StorageController;

public class MainActivity extends Activity implements ToolsMenu.Host {
    private static final String TARGET_URL = "https://ys.mihoyo.com/cloud/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private FrameLayout mRoot;
    private GeckoView mGeckoView;
    private GeckoRuntime mRuntime;
    private GeckoSession mSession;
    private VirtualMouse mMouse;
    private KeyboardRouter mKeyboard;
    private ToolsMenu mTools;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        DecoderMode.init(getApplicationContext());

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.BLACK);
        setContentView(mRoot);

        buildEngine();
        buildInput();

        mTools = new ToolsMenu(this, this);
        enterImmersive();
        loadTarget();
    }

    private void buildEngine() {
        String configPath = EnginePrefs.prepare(getApplicationContext());

        GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                .configFilePath(configPath)
                .aboutConfigEnabled(true)
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(true)
                .consoleOutput(true)
                .inputAutoZoomEnabled(false)
                .doubleTapZoomingEnabled(false)
                .build();

        mRuntime = GeckoRuntime.create(this, runtimeSettings);

        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .userAgentOverride(DESKTOP_UA)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
                .allowJavascript(true)
                .usePrivateMode(false)
                .build();

        mSession = new GeckoSession(sessionSettings);
        attachDelegates();

        mGeckoView = new GeckoView(this);
        mGeckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        mGeckoView.coverUntilFirstPaint(Color.BLACK);
        mGeckoView.setSession(mSession);
        mGeckoView.setFocusable(true);
        mGeckoView.setFocusableInTouchMode(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        mRoot.addView(mGeckoView, params);
    }

    private void attachDelegates() {
        mSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                enterImmersive();
            }

            @Override
            public void onTitleChange(GeckoSession session, String title) {
            }

            @Override
            public void onCloseRequest(GeckoSession session) {
                finish();
            }
        });

        mSession.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session,
                    ContentPermission permission) {
                return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
            }

            @Override
            public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions,
                    Callback callback) {
                callback.grant();
            }

            @Override
            public void onMediaPermissionRequest(GeckoSession session, String uri,
                    MediaSource[] video, MediaSource[] audio, MediaCallback callback) {
                callback.reject();
            }
        });

        mSession.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override
            public void onActivated(GeckoSession session, MediaSession mediaSession) {
            }

            @Override
            public void onDeactivated(GeckoSession session, MediaSession mediaSession) {
            }
        });

        mSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
            }
        });
    }

    private void buildInput() {
        mMouse = new VirtualMouse(mRoot, mGeckoView);
        mKeyboard = new KeyboardRouter(mMouse);
        mKeyboard.onMenuRequested = new Runnable() {
            @Override
            public void run() {
                mMouse.stopAll();
                mTools.show();
            }
        };
        mKeyboard.onBackRequested = new Runnable() {
            @Override
            public void run() {
                if (mTools.isShowing()) {
                    mTools.dismiss();
                } else {
                    injectEscape();
                }
            }
        };
    }

    private void loadTarget() {
        mGeckoView.post(new Runnable() {
            @Override
            public void run() {
                mMouse.onLayoutReady();
                mGeckoView.requestFocus();
                mSession.loadUri(TARGET_URL);
            }
        });
    }

    private void injectEscape() {
        long now = android.os.SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0);
        KeyEvent up = new KeyEvent(now, now + 16, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0);
        mGeckoView.dispatchKeyEvent(down);
        mGeckoView.dispatchKeyEvent(up);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mTools != null && mTools.isShowing()) {
            return super.dispatchKeyEvent(event);
        }
        if (mKeyboard != null && mKeyboard.route(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersive();
        }
    }

    private void enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersive();
    }

    @Override
    public void reloadPage() {
        if (mSession != null) {
            mSession.reload();
        }
    }

    @Override
    public void clearCacheAndReload() {
        if (mRuntime == null) {
            return;
        }
        mRuntime.getStorageController().clearData(StorageController.ClearFlags.ALL);
        mGeckoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSession != null) {
                    mSession.reload();
                }
            }
        }, 300);
    }

    @Override
    public void showDiagnostics() {
        new AlertDialog.Builder(this)
                .setTitle("故障诊断")
                .setMessage(buildDiagnostics())
                .setPositiveButton("确定", null)
                .show();
    }

    private String buildDiagnostics() {
        StringBuilder builder = new StringBuilder();
        builder.append("设备：").append(Build.MODEL).append('\n');
        builder.append("系统：Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        builder.append("ABI：").append(Build.CPU_ABI).append('\n');
        builder.append("解码输出：")
                .append(DecoderMode.isByteBuffer() ? "字节缓冲" : "纹理").append('\n');
        builder.append("硬件解码：").append(DecoderMode.hardwareDecode() ? "开启" : "关闭").append('\n');
        builder.append("页面：").append(TARGET_URL);
        return builder.toString();
    }

    @Override
    public void exitApp() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (mMouse != null) {
            mMouse.stopAll();
        }
        if (mGeckoView != null) {
            mGeckoView.releaseSession();
        }
        if (mSession != null) {
            mSession.close();
        }
        if (mRuntime != null) {
            mRuntime.shutdown();
        }
        super.onDestroy();
    }
}
