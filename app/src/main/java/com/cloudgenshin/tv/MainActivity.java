package com.cloudgenshin.tv;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebNotification;
import org.mozilla.geckoview.WebNotificationDelegate;
import org.mozilla.geckoview.WebRequestError;

public class MainActivity extends Activity {
    private static final String TARGET_URL = "https://ys.mihoyo.com/cloud/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0";

    private FrameLayout mRoot;
    private GeckoView mGeckoView;
    private GeckoRuntime mRuntime;
    private GeckoSession mSession;
    private VirtualMouse mVirtualMouse;
    private KeyboardRouter mKeyboardRouter;
    private ToolsMenu mToolsMenu;
    private TextView mOverlay;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mCrashCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        DecoderMode.init(this);
        EngineMode.init(this);

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.BLACK);
        setContentView(mRoot);

        buildEngine();
        buildOverlay();
        buildInput();
        showStatus("正在打开云·原神…");
    }

    private void buildEngine() {
        GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .webFontsEnabled(true)
                .aboutConfigEnabled(true)
                .consoleOutput(false)
                .contentBlocking(new ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.NONE)
                        .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
                        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                        .build())
                .configFilePath(EnginePrefs.prepare(this))
                .build();

        mRuntime = GeckoRuntime.create(this, runtimeSettings);
        mRuntime.setWebNotificationDelegate(new WebNotificationDelegate() {
            @Override
            public void onShowNotification(WebNotification notification) {
            }

            @Override
            public void onCloseNotification(WebNotification notification) {
                notification.dismiss();
            }
        });

        mGeckoView = new GeckoView(this);
        mGeckoView.setFocusable(true);
        mGeckoView.setFocusableInTouchMode(true);
        mGeckoView.setViewBackend(EngineMode.backend() == EngineMode.BACKEND_SURFACE
                ? GeckoView.BACKEND_SURFACE_VIEW
                : GeckoView.BACKEND_TEXTURE_VIEW);
        mGeckoView.coverUntilFirstPaint(Color.BLACK);
        mRoot.addView(mGeckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
                .userAgentOverride(DESKTOP_UA)
                .allowJavascript(true)
                .usePrivateMode(false)
                .build();

        mSession = new GeckoSession(sessionSettings);
        attachDelegates();
        mGeckoView.setSession(mSession);
        if (!mSession.isOpen()) {
            mSession.open(mRuntime);
        }
        mGeckoView.requestFocus();
        mSession.loadUri(TARGET_URL);
    }

    private void attachDelegates() {
        mSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
            }

            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                if (fullScreen) {
                    mVirtualMouse.hide();
                }
            }

            @Override
            public void onCloseRequest(GeckoSession session) {
                moveTaskToBack(true);
            }

            @Override
            public void onCrash(GeckoSession session) {
                recoverContent("网页进程崩溃，正在自动恢复…");
            }

            @Override
            public void onKill(GeckoSession session) {
                recoverContent("系统内存不足，网页被终止，正在恢复…");
            }
        });

        mSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                showStatus("正在加载云·原神…");
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                if (success) {
                    hideStatus();
                }
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
            }
        });

        mSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<String> onLoadError(GeckoSession session, String uri,
                    WebRequestError error) {
                showError(uri, error);
                return null;
            }
        });
    }

    private void recoverContent(final String message) {
        mCrashCount++;
        showStatus(message);
        if (mCrashCount > 2) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    restartApp();
                }
            }, 1500);
            return;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSession.isOpen()) {
                    mSession.reload();
                } else if (mRuntime != null) {
                    mSession.open(mRuntime);
                    mSession.loadUri(TARGET_URL);
                }
            }
        }, 1500);
    }

    private void buildOverlay() {
        mOverlay = new TextView(this);
        mOverlay.setGravity(Gravity.CENTER);
        mOverlay.setTextColor(Color.WHITE);
        mOverlay.setTextSize(18);
        mOverlay.setPadding(48, 36, 48, 36);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xE6101826);
        drawable.setCornerRadius(18);
        mOverlay.setBackground(drawable);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        mOverlay.setLayoutParams(params);
        mOverlay.setVisibility(View.GONE);
        mRoot.addView(mOverlay);
    }

    private void buildInput() {
        mVirtualMouse = new VirtualMouse(mRoot, mGeckoView);
        mKeyboardRouter = new KeyboardRouter(mVirtualMouse);
        mKeyboardRouter.onMenuRequested = new Runnable() {
            @Override
            public void run() {
                mToolsMenu.show();
            }
        };
        mKeyboardRouter.onBackRequested = new Runnable() {
            @Override
            public void run() {
                if (mSession != null && mSession.isOpen()) {
                    mSession.goBack();
                } else {
                    moveTaskToBack(true);
                }
            }
        };
        mToolsMenu = new ToolsMenu(this, new ToolsMenu.Host() {
            @Override
            public void reloadPage() {
                if (mSession != null && mSession.isOpen()) {
                    mSession.reload();
                }
            }

            @Override
            public void clearCacheAndReload() {
                if (mRuntime != null) {
                    mRuntime.getStorageController().clearData(StorageController.ClearFlags.ALL);
                }
                if (mSession != null && mSession.isOpen()) {
                    mSession.reload();
                }
            }

            @Override
            public void showDiagnostics() {
                showDiagnosticsInfo();
            }

            @Override
            public void restartEngine() {
                restartApp();
            }

            @Override
            public void exitApp() {
                moveTaskToBack(true);
            }
        });
    }

    private void showStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlay.setText(text);
                mOverlay.setVisibility(View.VISIBLE);
                mOverlay.bringToFront();
            }
        });
    }

    private void hideStatus() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void showError(final String uri, final WebRequestError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StringBuilder builder = new StringBuilder();
                builder.append("无法打开云·原神\n");
                builder.append(uri).append('\n');
                builder.append("错误代码：").append(error.code);
                mOverlay.setText(builder.toString());
                mOverlay.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showDiagnosticsInfo() {
        StringBuilder builder = new StringBuilder();
        builder.append("渲染后端：").append(EngineMode.backend() == EngineMode.BACKEND_SURFACE
                ? "SurfaceView" : "TextureView").append('\n');
        builder.append("图形模式：").append(graphicsLabel()).append('\n');
        builder.append("解码输出：").append(DecoderMode.isByteBuffer() ? "字节缓冲" : "纹理").append('\n');
        builder.append("硬件解码：").append(DecoderMode.hardwareDecode() ? "开启" : "关闭").append('\n');
        builder.append("会话状态：")
                .append(mSession != null && mSession.isOpen() ? "已打开" : "未打开").append('\n');
        builder.append("GL_RENDERER：").append(glRenderer()).append('\n');
        builder.append("目标：").append(TARGET_URL);
        showStatus(builder.toString());
    }

    private String graphicsLabel() {
        int graphics = EngineMode.graphics();
        if (graphics == EngineMode.GRAPHICS_SOFTWARE) {
            return "软件 WebRender";
        }
        if (graphics == EngineMode.GRAPHICS_NO_COMPOSITOR) {
            return "硬件（合成器关闭）";
        }
        return "硬件 WebRender";
    }

    private String glRenderer() {
        try {
            return GLES20.glGetString(GLES20.GL_RENDERER);
        } catch (Exception e) {
            return "未知";
        }
    }

    private void restartApp() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 400, pending);
        Runtime.getRuntime().exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mToolsMenu != null && mToolsMenu.isShowing()) {
            return super.dispatchKeyEvent(event);
        }
        if (mKeyboardRouter != null && mKeyboardRouter.route(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mSession != null) {
            mSession.setActive(true);
        }
    }
}
