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
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
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
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.setStatusBarColor(Color.parseColor("#06070C"));
        window.setNavigationBarColor(Color.parseColor("#06070C"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Display display = getDisplay();
                if (display != null) {
                    Display.Mode[] modes = display.getSupportedModes();
                    Display.Mode best = null;
                    for (Display.Mode m : modes) {
                        if (best == null || m.getRefreshRate() > best.getRefreshRate()) {
                            best = m;
                        }
                    }
                    if (best != null) {
                        WindowManager.LayoutParams lp = window.getAttributes();
                        lp.preferredDisplayModeId = best.getModeId();
                        window.setAttributes(lp);
                    }
                }
            } catch (Exception ignored) {}
        }

        RootBridge.startBackgroundPoller();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#06070C"));

        uiWebView = new WebView(this);
        uiWebView.setBackgroundColor(Color.parseColor("#06070C"));
        uiWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        uiWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings ws = uiWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        uiWebView.addJavascriptInterface(new BridgeImpl(), "NamelessBridge");
        uiWebView.setWebChromeClient(new WebChromeClient());
        uiWebView.setWebViewClient(new WebViewClient());

        checkerContainer = new FrameLayout(this);
        checkerContainer.setVisibility(View.GONE);
        checkerContainer.setBackgroundColor(Color.parseColor("#0B0E18"));

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

        ioPool.execute(() -> RootBridge.ensureBinariesExtracted(getApplicationContext()));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void mountAndLoadChecker(String url, float topRatio, float bottomRatio, boolean forceReload) {
        int totalH = rootLayout.getHeight();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (totalH <= 0) totalH = dm.heightPixels;

        int topPx = Math.max(Math.round(118 * dm.density), Math.round(topRatio * totalH));
        int bottomPx = Math.max(Math.round(74 * dm.density), Math.round(bottomRatio * totalH));
        int sidePx = Math.round(12 * dm.density);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        lp.setMargins(sidePx, topPx, sidePx, bottomPx);
        lp.gravity = Gravity.TOP;
        checkerContainer.setLayoutParams(lp);
        checkerContainer.setVisibility(View.VISIBLE);

        boolean firstCreate = false;
        if (checkerWebView == null) {
            firstCreate = true;
            checkerWebView = new WebView(this);
            checkerWebView.setBackgroundColor(Color.parseColor("#0B0E18"));
            checkerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            WebSettings s = checkerWebView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            );
            checkerWebView.setWebChromeClient(new WebChromeClient());
            checkerWebView.setWebViewClient(new WebViewClient() {
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
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }
            });
            checkerContainer.addView(checkerWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }

        if (url != null && !url.isEmpty()) {
            String current = checkerWebView.getUrl();
            if (firstCreate || forceReload || current == null || !current.startsWith(url)) {
                checkerWebView.loadUrl(url);
            }
        }
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
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "LTE • " + iface;
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
            RootBridge.scheduleSaveSlots(getApplicationContext(), jsonStr);
        }

        @JavascriptInterface
        public boolean saveSlotsNow(String jsonStr) {
            return RootBridge.saveAllSlotsToDisk(getApplicationContext(), jsonStr);
        }

        @JavascriptInterface
        public void startTunnelAsync(String configJsonStr) {
            ioPool.execute(() -> {
                try {
                    JSONObject cfg = new JSONObject(configJsonStr);
                    JSONObject r = RootBridge.startTunnel(getApplicationContext(), cfg);
                    final String payload = r.toString().replace("\\", "\\\\").replace("'", "\\'");
                    runOnUiThread(() -> {
                        if (checkerWebView != null && checkerContainer.getVisibility() == View.VISIBLE) {
                            checkerWebView.reload();
                        }
                        uiWebView.evaluateJavascript(
                            "window.onTunnelActionComplete && window.onTunnelActionComplete('" + payload + "');",
                            null
                        );
                    });
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
            ioPool.execute(() -> {
                RootBridge.stopTunnel(getApplicationContext());
                runOnUiThread(() -> uiWebView.evaluateJavascript(
                    "window.onTunnelActionComplete && window.onTunnelActionComplete('{\"ok\":true,\"stopped\":true}');",
                    null
                ));
            });
        }

        @JavascriptInterface
        public void pingServerAsync(String host, int port) {
            ioPool.execute(() -> {
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
            runOnUiThread(() -> mountAndLoadChecker(url, topRatio, bottomRatio, false));
        }

        @JavascriptInterface
        public void showCheckerView(String url, int topCssPx, int bottomCssPx) {
            runOnUiThread(() -> mountAndLoadChecker(url, 0.165f, 0.105f, false));
        }

        @JavascriptInterface
        public void hideCheckerView() {
            runOnUiThread(() -> {
                if (checkerContainer != null) {
                    checkerContainer.setVisibility(View.GONE);
                }
            });
        }

        @JavascriptInterface
        public void navigateChecker(String url) {
            runOnUiThread(() -> mountAndLoadChecker(url, 0.165f, 0.105f, true));
        }

        @JavascriptInterface
        public void reloadChecker(boolean wipeData) {
            runOnUiThread(() -> {
                if (checkerWebView != null) {
                    if (wipeData) {
                        try {
                            CookieManager.getInstance().removeAllCookies(null);
                            CookieManager.getInstance().flush();
                            WebStorage.getInstance().deleteAllData();
                            checkerWebView.clearCache(true);
                            checkerWebView.clearHistory();
                        } catch (Exception ignored) {}
                    }
                    checkerWebView.reload();
                }
            });
        }

        @JavascriptInterface
        public String fetchLogs(boolean audit) {
            return RootBridge.getLogsOrAudit(audit);
        }

        @JavascriptInterface
        public void clearKernelLogs() {
            ioPool.execute(RootBridge::clearLogs);
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
    public void onBackPressed() {
        if (checkerContainer != null && checkerContainer.getVisibility() == View.VISIBLE && checkerWebView != null && checkerWebView.canGoBack()) {
            checkerWebView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
