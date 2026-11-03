package com.nameless.proxy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.nameless.proxy.core.RootBridge;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private FrameLayout rootLayout;
    private WebView uiWebView;
    private WebView checkerWebView;
    private FrameLayout checkerContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService tunnelPool = Executors.newSingleThreadExecutor();
    private final ExecutorService savePool = Executors.newSingleThreadExecutor();
    private final ExecutorService pingPool = Executors.newCachedThreadPool();

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.setStatusBarColor(Color.parseColor("#090A0D"));
        window.setNavigationBarColor(Color.parseColor("#0D0F14"));

        RootBridge.startBackgroundPoller();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#090A0D"));

        uiWebView = new WebView(this);
        uiWebView.setBackgroundColor(Color.parseColor("#090A0D"));
        uiWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        uiWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        uiWebView.setVerticalScrollBarEnabled(false);
        uiWebView.setHorizontalScrollBarEnabled(false);

        WebSettings ws = uiWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // Lock textZoom to 100% so phone font-size or custom DPI scaling never distorts UI layout or icon alignment
        ws.setTextZoom(100);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        uiWebView.addJavascriptInterface(new BridgeImpl(), "NamelessBridge");
        uiWebView.setWebChromeClient(new WebChromeClient());
        uiWebView.setWebViewClient(new WebViewClient());

        checkerContainer = new FrameLayout(this);
        checkerContainer.setVisibility(View.GONE);
        checkerContainer.setBackgroundColor(Color.parseColor("#090A0D"));

        rootLayout.addView(uiWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(checkerContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);
        uiWebView.loadUrl("file:///android_asset/ui/index.html");

        tunnelPool.execute(() -> RootBridge.ensureBinariesExtracted(getApplicationContext()));
    }

    /**
     * Completely closes and destroys the checker WebView when switching away to any other tab.
     */
    private void closeAndDestroyCheckerWebView() {
        mainHandler.removeCallbacksAndMessages(null);
        if (checkerContainer != null) {
            checkerContainer.setVisibility(View.GONE);
            checkerContainer.removeAllViews();
        }
        if (checkerWebView != null) {
            try {
                checkerWebView.stopLoading();
                checkerWebView.loadUrl("about:blank");
                checkerWebView.clearHistory();
                checkerWebView.clearCache(true);
                checkerWebView.destroy();
            } catch (Exception ignored) {}
            checkerWebView = null;
        }
    }

    /**
     * Aligns checkerContainer using exact dimensionless viewport ratios from #checkerPlaceholder
     * so it never conflicts with phone DPI, and immediately runs a fresh test.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void openAndAutoRunChecker(final String url, float topRatio, float bottomRatio, float sideRatio) {
        if (url == null || url.isEmpty()) return;

        closeAndDestroyCheckerWebView();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int totalW = uiWebView != null && uiWebView.getWidth() > 0 ? uiWebView.getWidth() : dm.widthPixels;
        int totalH = uiWebView != null && uiWebView.getHeight() > 0 ? uiWebView.getHeight() : dm.heightPixels;

        int topPx = Math.max(0, Math.round(topRatio * totalH));
        int bottomPx = Math.max(0, Math.round(bottomRatio * totalH));
        int sidePx = Math.max(0, Math.round(sideRatio * totalW));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        lp.setMargins(sidePx, topPx, sidePx, bottomPx);
        lp.gravity = Gravity.TOP;
        checkerContainer.setLayoutParams(lp);
        checkerContainer.setVisibility(View.VISIBLE);

        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {}

        checkerWebView = new WebView(this);
        checkerWebView.setBackgroundColor(Color.parseColor("#090A0D"));
        checkerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = checkerWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        );
        checkerWebView.setWebChromeClient(new WebChromeClient());
        checkerWebView.setWebViewClient(new WebViewClient() {
            private boolean retriedOnce = false;

            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                super.onPageStarted(view, pageUrl, favicon);
                notifyUiCheckerUrl(pageUrl, true);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                notifyUiCheckerUrl(pageUrl, false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame() && !retriedOnce) {
                    retriedOnce = true;
                    mainHandler.postDelayed(() -> {
                        if (checkerWebView != null && checkerContainer.getVisibility() == View.VISIBLE) {
                            checkerWebView.loadUrl(url);
                        }
                    }, 600);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        checkerContainer.addView(checkerWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        checkerWebView.loadUrl(url);
    }

    private void notifyUiCheckerUrl(String url, boolean loading) {
        runOnUiThread(() -> {
            if (uiWebView != null) {
                String safeUrl = url == null ? "" : url.replace("'", "\\'");
                uiWebView.evaluateJavascript(
                    "window.onCheckerState && window.onCheckerState('" + safeUrl + "', " + loading + ");",
                    null
                );
            }
        });
    }

    private String getActiveNetworkSummary() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Online";
            Network net = cm.getActiveNetwork();
            if (net == null) return "Offline";
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            LinkProperties lp = cm.getLinkProperties(net);
            String iface = (lp != null && lp.getInterfaceName() != null) ? lp.getInterfaceName() : "auto";
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi • " + iface;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile • " + iface;
            }
            return iface;
        } catch (Exception e) {
            return "Online";
        }
    }

    public class BridgeImpl {

        @JavascriptInterface
        public String getInitialState() {
            try {
                JSONObject res = new JSONObject();
                res.put("root", true);
                res.put("userId", RootBridge.getAndroidUserId());
                res.put("network", getActiveNetworkSummary());
                res.put("slotsJson", RootBridge.loadAllSlotsFromDisk(getApplicationContext()));
                res.put("status", RootBridge.getTunnelStatusJson());
                return res.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getInstalledApps() {
            return RootBridge.getInstalledAppsJson(getApplicationContext());
        }

        @JavascriptInterface
        public String pollStatus() {
            try {
                JSONObject s = RootBridge.getTunnelStatusJson();
                s.put("network", getActiveNetworkSummary());
                return s.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void saveSlots(String jsonStr) {
            final Context appCtx = getApplicationContext();
            appCtx.getSharedPreferences("nameless_v2_prefs", Context.MODE_PRIVATE)
                .edit().putString("slots_json", jsonStr).apply();
            savePool.execute(() -> RootBridge.saveAllSlotsToDisk(appCtx, jsonStr));
        }

        @JavascriptInterface
        public void startTunnelAsync(String configJsonStr) {
            tunnelPool.execute(() -> {
                try {
                    JSONObject cfg = new JSONObject(configJsonStr);
                    JSONObject r = RootBridge.startTunnel(getApplicationContext(), cfg);
                    final String payload = r.toString().replace("\\", "\\\\").replace("'", "\\'");
                    runOnUiThread(() -> uiWebView.evaluateJavascript(
                        "window.onTunnelActionComplete && window.onTunnelActionComplete('" + payload + "');",
                        null
                    ));
                } catch (Exception e) {
                    runOnUiThread(() -> uiWebView.evaluateJavascript(
                        "window.onTunnelActionComplete && window.onTunnelActionComplete('{\"ok\":false,\"error\":\"Invalid config\"}');",
                        null
                    ));
                }
            });
        }

        @JavascriptInterface
        public void stopTunnelAsync() {
            tunnelPool.execute(() -> {
                RootBridge.stopTunnel(getApplicationContext());
                runOnUiThread(() -> uiWebView.evaluateJavascript(
                    "window.onTunnelActionComplete && window.onTunnelActionComplete('{\"ok\":true,\"stopped\":true}');",
                    null
                ));
            });
        }

        @JavascriptInterface
        public void pingServerAsync(String host, int port) {
            pingPool.execute(() -> {
                int ms = RootBridge.pingServer(host, port);
                runOnUiThread(() -> uiWebView.evaluateJavascript(
                    "window.onPingResult && window.onPingResult(" + ms + ");",
                    null
                ));
            });
        }

        @JavascriptInterface
        public String importClipboardUri() {
            try {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null && cb.hasPrimaryClip()) {
                    ClipData clip = cb.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).coerceToText(MainActivity.this);
                        if (text != null) {
                            return RootBridge.parseProxyUri(text.toString()).toString();
                        }
                    }
                }
            } catch (Exception ignored) {}
            return "{}";
        }

        @JavascriptInterface
        public void showCheckerRatio(String url, float topRatio, float bottomRatio) {
            runOnUiThread(() -> openAndAutoRunChecker(url, topRatio, bottomRatio, 0.025f));
        }

        @JavascriptInterface
        public void showCheckerBounds(String url, float topRatio, float bottomRatio, float sideRatio) {
            runOnUiThread(() -> openAndAutoRunChecker(url, topRatio, bottomRatio, sideRatio));
        }

        @JavascriptInterface
        public void hideCheckerView() {
            runOnUiThread(MainActivity.this::closeAndDestroyCheckerWebView);
        }

        @JavascriptInterface
        public void navigateChecker(String url) {
            runOnUiThread(() -> openAndAutoRunChecker(url, 0.135f, 0.085f, 0.025f));
        }

        @JavascriptInterface
        public String fetchLogs(boolean audit) {
            return RootBridge.getLogsOrAudit(audit);
        }

        @JavascriptInterface
        public void clearKernelLogs() {
            savePool.execute(RootBridge::clearLogs);
        }

        @JavascriptInterface
        public boolean exportAllSlots() {
            return RootBridge.exportBackup(getApplicationContext());
        }

        @JavascriptInterface
        public String importAllSlots() {
            return RootBridge.importBackup(getApplicationContext());
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        closeAndDestroyCheckerWebView();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (checkerContainer != null && checkerContainer.getVisibility() == View.VISIBLE) {
            if (checkerWebView != null && checkerWebView.canGoBack()) {
                checkerWebView.goBack();
                return;
            }
            if (uiWebView != null) {
                uiWebView.evaluateJavascript("switchTab(0);", null);
                return;
            }
        }
        super.onBackPressed();
    }
}
