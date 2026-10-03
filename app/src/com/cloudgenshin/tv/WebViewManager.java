package com.cloudgenshin.tv;

import android.annotation.SuppressLint;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class WebViewManager {

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String CLOUD_GENSHIN_URL = "https://ys.mihoyo.com/cloud/";

    private final WebView webView;

    public WebViewManager(WebView webView) {
        this.webView = webView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void configure() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);
        settings.setAppCacheMaxSize(1024 * 1024 * 64);

        settings.setUserAgentString(DESKTOP_UA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    public void loadCloudGenshin() {
        webView.loadUrl(CLOUD_GENSHIN_URL);
    }

    public void reload() {
        webView.reload();
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    public void goBack() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    public WebView getWebView() {
        return webView;
    }

    public static String getDesktopUserAgent() {
        return DESKTOP_UA;
    }

    public static String getCloudGenshinUrl() {
        return CLOUD_GENSHIN_URL;
    }
}
