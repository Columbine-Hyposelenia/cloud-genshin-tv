package com.cloudgenshin.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final long MOUSE_HIDE_DELAY_MS = 5000;

    private FrameLayout rootLayout;
    private WebView webView;
    private WebViewManager webViewManager;
    private VirtualMouse virtualMouse;
    private KeyboardMapper keyboardMapper;
    private CompatibilityInjector compatibilityInjector;
    private ProgressBar progressBar;
    private TextView statusText;
    private View errorView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMouseRunnable = new Runnable() {
        @Override
        public void run() {
            if (virtualMouse != null) {
                virtualMouse.hide();
            }
        }
    };

    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isNetworkAvailable()) {
                if (errorView != null && errorView.getVisibility() == View.VISIBLE) {
                    errorView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    webViewManager.reload();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        rootLayout.addView(webView, webViewParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(4)));
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        rootLayout.addView(progressBar);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(android.view.Gravity.CENTER);
        statusText.setText("正在加载云原神...");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
        rootLayout.addView(statusText, statusParams);

        errorView = createErrorView();
        errorView.setVisibility(View.GONE);
        rootLayout.addView(errorView);

        setContentView(rootLayout);

        webViewManager = new WebViewManager(webView);
        webViewManager.configure();

        keyboardMapper = new KeyboardMapper(webView);
        compatibilityInjector = new CompatibilityInjector(webView);

        virtualMouse = new VirtualMouse(this, webView);
        virtualMouse.addToWindow(rootLayout);
        virtualMouse.setScrollListener(new VirtualMouse.ScrollListener() {
            @Override
            public void onScroll(float vertical, float horizontal) {
                if (vertical > 0) {
                    webView.pageUp(false);
                } else if (vertical < 0) {
                    webView.pageDown(false);
                }
                if (horizontal != 0) {
                    webView.scrollBy((int) (horizontal * webView.getWidth() / 2), 0);
                }
            }
        });

        setupWebViewClients();

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);

        loadCloudGenshin();
    }

    private View createErrorView() {
        FrameLayout layout = new FrameLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(Color.BLACK);

        TextView text = new TextView(this);
        text.setText("网络连接失败\n请检查网络后按遥控器确定键重试");
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
        layout.addView(text, textParams);

        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNetworkAvailable()) {
                    errorView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    webViewManager.reload();
                } else {
                    Toast.makeText(MainActivity.this, "网络仍不可用", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return layout;
    }

    private void setupWebViewClients() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                statusText.setVisibility(View.VISIBLE);
                statusText.setText("正在加载云原神...");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                statusText.setVisibility(View.GONE);
                compatibilityInjector.inject();
                scheduleMouseHide();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                showError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                if (newProgress > 10) {
                    statusText.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return true;
            }
        });
    }

    private void loadCloudGenshin() {
        if (isNetworkAvailable()) {
            webViewManager.loadCloudGenshin();
        } else {
            showError();
        }
    }

    private void showError() {
        statusText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void scheduleMouseHide() {
        handler.removeCallbacks(hideMouseRunnable);
        handler.postDelayed(hideMouseRunnable, MOUSE_HIDE_DELAY_MS);
    }

    private void wakeMouse() {
        if (!virtualMouse.isVisible()) {
            virtualMouse.show();
        }
        scheduleMouseHide();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (webViewManager.canGoBack()) {
                    webViewManager.goBack();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (action == KeyEvent.ACTION_DOWN) {
                webViewManager.reload();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyboardMapper.shouldUseVirtualMouse(keyCode)) {
            wakeMouse();
            if (action == KeyEvent.ACTION_DOWN) {
                return virtualMouse.handleKeyDown(keyCode, event);
            } else if (action == KeyEvent.ACTION_UP) {
                return virtualMouse.handleKeyUp(keyCode, event);
            }
            return true;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyboardMapper.handleKeyDown(keyCode, event)) {
                return true;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyboardMapper.handleKeyUp(keyCode, event)) {
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            float x = event.getX();
            float y = event.getY();
            virtualMouse.showAt(x, y);
            scheduleMouseHide();
            return webView.dispatchTouchEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        keyboardMapper.refreshKeyboardState();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        webView.pauseTimers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(hideMouseRunnable);
        try {
            unregisterReceiver(networkReceiver);
        } catch (Throwable ignored) {}
        if (virtualMouse != null) {
            virtualMouse.releaseAll();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeAllViews();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
