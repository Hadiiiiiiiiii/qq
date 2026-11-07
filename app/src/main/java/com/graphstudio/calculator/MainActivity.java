package com.graphstudio.calculator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private static final int LIGHT_BG = 0xFFF2F2F7;
    private static final int DARK_BG = 0xFF000000;

    private FrameLayout root;
    private WebView webView;
    private SharedPreferences prefs;

    private boolean lightMode = false;
    private boolean pageReady = false;

    private int insetTop = 0;
    private int insetBottom = 0;
    private int insetLeft = 0;
    private int insetRight = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("graph_studio", MODE_PRIVATE);
        lightMode = prefs.getBoolean("light_mode", false);

        // Edge-to-edge: the page is drawn behind transparent system bars.
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();
        applyThemeColors();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            readInsets(insets);
            applyInsetsToWebView();
            pushInsetsToPage();
            return insets;
        });
        root.requestApplyInsets();

        webView.loadUrl("file:///android_asset/index.html");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        webView.addJavascriptInterface(new ThemeBridge(), "AndroidTheme");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                pushInsetsToPage();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void readInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = i.top;
            insetBottom = i.bottom;
            insetLeft = i.left;
            insetRight = i.right;
        } else {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            insetLeft = insets.getSystemWindowInsetLeft();
            insetRight = insets.getSystemWindowInsetRight();
        }
    }

    // The page starts below the status bar and runs all the way to the bottom edge of the screen.
    // The page itself keeps the keys a small distance above the bottom edge (see index.html).
    private void applyInsetsToWebView() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webView.getLayoutParams();
        if (lp.topMargin != insetTop || lp.leftMargin != insetLeft
                || lp.rightMargin != insetRight || lp.bottomMargin != 0) {
            lp.topMargin = insetTop;
            lp.leftMargin = insetLeft;
            lp.rightMargin = insetRight;
            lp.bottomMargin = 0;
            webView.setLayoutParams(lp);
        }
    }

    private void pushInsetsToPage() {
        if (!pageReady) return;
        float density = getResources().getDisplayMetrics().density;
        float bottomCssPx = insetBottom / density;
        String js = "(function(){var s=document.documentElement.style;"
                + "s.setProperty('--gs-inset-bottom','" + bottomCssPx + "px');"
                + "window.dispatchEvent(new Event('gs-insets'));})();";
        webView.evaluateJavascript(js, null);
    }

    // Status bar / navigation bar colour and icon colour follow the app theme.
    private void applyThemeColors() {
        int bg = lightMode ? LIGHT_BG : DARK_BG;
        root.setBackgroundColor(bg);
        webView.setBackgroundColor(bg);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(lightMode ? mask : 0, mask);
            }
        } else {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            flags = lightMode ? (flags | lightFlags) : (flags & ~lightFlags);
            decor.setSystemUiVisibility(flags);
        }
    }

    // Called from the page: window.AndroidTheme.setLightMode(true|false)
    private class ThemeBridge {
        @JavascriptInterface
        public void setLightMode(final boolean light) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    lightMode = light;
                    prefs.edit().putBoolean("light_mode", light).apply();
                    applyThemeColors();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        root.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
