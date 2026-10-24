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

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import com.nameless.proxy.core.RootBridge;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView uiWebView;
    private WebView checkerWebView;
    private FrameLayout checkerContainer;
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    private static final String WEBRTC_HARDENED_JS =
        "(function(){" +
        "try{" +
        "const orig = window.RTCPeerConnection || window.webkitRTCPeerConnection;" +
        "if(!orig || orig.__namelessPatched) return;" +
        "function isHostCand(c){" +
        "if(!c) return false;" +
        "return /typ host/i.test(c);" +
        "}" +
        "const Wrapped = function(cfg, constraints){" +
        "const pc = new orig(cfg, constraints);" +
        "pc.addEventListener('icecandidate', function(e){" +
        "if(e && e.candidate && e.candidate.candidate && isHostCand(e.candidate.candidate)){" +
        "e.stopImmediatePropagation();" +
        "}" +
        "}, true);" +
        "return pc;" +
        "};" +
        "Wrapped.prototype = orig.prototype;" +
        "Wrapped.__namelessPatched = true;" +
        "window.RTCPeerConnection = Wrapped;" +
        "window.webkitRTCPeerConnection = Wrapped;" +
        "}catch(e){}" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.setStatusBarColor(Color.parseColor("#050811"));
        window.setNavigationBarColor(Color.parseColor("#050811"));

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

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#050811"));

        uiWebView = new WebView(this);
        uiWebView.setBackgroundColor(Color.parseColor("#050811"));
        uiWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        checkerContainer.setBackgroundColor(Color.parseColor("#080D1A"));

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

    private void updateWebViewProxy(boolean active) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                if (active) {
                    ProxyConfig cfg = new ProxyConfig.Builder()
                        .addProxyRule("127.0.0.1:10801")
                        .addBypassRule("localhost")
                        .addBypassRule("127.0.0.1")
                        .build();
                    ProxyController.getInstance().setProxyOverride(cfg, Runnable::run, () -> {});
                } else {
                    ProxyController.getInstance().clearProxyOverride(Runnable::run, () -> {});
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureCheckerWebView(String url, int topDp, int bottomDp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int topPx = Math.round(topDp * dm.density);
        int bottomPx = Math.round(bottomDp * dm.density);
        int sidePx = Math.round(12 * dm.density);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        lp.setMargins(sidePx, topPx, sidePx, bottomPx);
        lp.gravity = Gravity.TOP;
        checkerContainer.setLayoutParams(lp);
        checkerContainer.setVisibility(View.VISIBLE);

        if (checkerWebView == null) {
            checkerWebView = new WebView(this);
            checkerWebView.setBackgroundColor(Color.parseColor("#080D1A"));
            checkerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            WebSettings s = checkerWebView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            );
            checkerWebView.setWebChromeClient(new WebChromeClient());
            checkerWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                    super.onPageStarted(view, pageUrl, favicon);
                    view.evaluateJavascript(WEBRTC_HARDENED_JS, null);
                    notifyUiCheckerUrl(pageUrl, true);
                }

                @Override
                public void onPageFinished(WebView view, String pageUrl) {
                    super.onPageFinished(view, pageUrl);
                    view.evaluateJavascript(WEBRTC_HARDENED_JS, null);
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
            checkerWebView.loadUrl(url);
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
                boolean root = RootBridge.checkRoot();
                res.put("root", root);
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
            ioPool.execute(() -> RootBridge.saveAllSlotsToDisk(getApplicationContext(), jsonStr));
        }

        @JavascriptInterface
        public void startTunnelAsync(String configJsonStr) {
            ioPool.execute(() -> {
                try {
                    JSONObject cfg = new JSONObject(configJsonStr);
                    JSONObject r = RootBridge.startTunnel(getApplicationContext(), cfg);
                    boolean ok = r.optBoolean("ok", false);
                    updateWebViewProxy(ok);
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
            ioPool.execute(() -> {
                RootBridge.stopTunnel(getApplicationContext());
                updateWebViewProxy(false);
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
        public void showCheckerView(String url, int topDp, int bottomDp) {
            runOnUiThread(() -> ensureCheckerWebView(url, topDp, bottomDp));
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
            runOnUiThread(() -> {
                if (checkerWebView != null && url != null && !url.isEmpty()) {
                    checkerWebView.loadUrl(url);
                }
            });
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
