package com.nameless.proxy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.nameless.proxy.core.RootBridge;

import org.json.JSONObject;

import java.util.Collections;
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
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.setStatusBarColor(Color.parseColor("#0B0F19"));
        window.setNavigationBarColor(Color.parseColor("#0F1522"));

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
        }

        RootBridge.startBackgroundPoller();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#0B0F19"));
        rootLayout.setFitsSystemWindows(true);

        // Enforce strict system-bar & 3-button navigation bar insets on all Android 10–15 ROMs
        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets sysBars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    );
                    Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    int bottom = Math.max(sysBars.bottom, ime.bottom);
                    v.setPadding(sysBars.left, sysBars.top, sysBars.right, bottom);
                    return WindowInsets.CONSUMED;
                } else {
                    v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                    );
                    return insets.consumeSystemWindowInsets();
                }
            }
        });

        uiWebView = new WebView(this);
        uiWebView.setBackgroundColor(Color.parseColor("#0B0F19"));
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
        ws.setTextZoom(100);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        uiWebView.addJavascriptInterface(new BridgeImpl(), "NamelessBridge");
        uiWebView.setWebChromeClient(new WebChromeClient());
        uiWebView.setWebViewClient(new WebViewClient());

        checkerContainer = new FrameLayout(this);
        checkerContainer.setVisibility(View.GONE);
        checkerContainer.setBackgroundColor(Color.parseColor("#0B0F19"));

        rootLayout.addView(uiWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(checkerContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);
        rootLayout.requestApplyInsets();
        uiWebView.loadUrl("file:///android_asset/ui/index.html");

        tunnelPool.execute(() -> {
            RootBridge.ensureBinariesExtracted(getApplicationContext());
            RootBridge.refreshStatusFromRootNow();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rootLayout != null) {
            rootLayout.requestApplyInsets();
        }
        tunnelPool.execute(RootBridge::refreshStatusFromRootNow);
    }

    /**
     * Builds the document-start WebRTC ICE & Timezone synchronization shim for checkerWebView.
     * WebRTC protection is ALWAYS ON whenever the tunnel is running—never leaks and never times out with NA.
     */
    private String buildCheckerDocumentStartScript() {
        boolean running = RootBridge.isTunnelRunningForCurrentUser();
        String exitIp = RootBridge.getCachedExitIp().replace("'", "");
        String exitTz = RootBridge.getCachedExitTimezone().replace("'", "");

        return "(function(){\n" +
            "  var TUNNEL_ON = " + (running ? "true" : "false") + ";\n" +
            "  var SPOOF_IP = '" + exitIp + "';\n" +
            "  var PROXY_TZ = '" + exitTz + "';\n" +
            "  if (TUNNEL_ON && PROXY_TZ && PROXY_TZ.indexOf('/') > 0) {\n" +
            "    try {\n" +
            "      var origDTF = Intl.DateTimeFormat;\n" +
            "      Intl.DateTimeFormat = function(loc, opts) {\n" +
            "        opts = Object.assign({}, opts || {});\n" +
            "        if (!opts.timeZone) opts.timeZone = PROXY_TZ;\n" +
            "        return new origDTF(loc, opts);\n" +
            "      };\n" +
            "      Intl.DateTimeFormat.prototype = origDTF.prototype;\n" +
            "      Intl.DateTimeFormat.supportedLocalesOf = origDTF.supportedLocalesOf;\n" +
            "    } catch(e) {}\n" +
            "  }\n" +
            "  var OrigPC = window.RTCPeerConnection || window.webkitRTCPeerConnection;\n" +
            "  if (!OrigPC) return;\n" +
            "  function WrappedPC(cfg, constraints) {\n" +
            "    var pc = new OrigPC(cfg, constraints);\n" +
            "    var deliveredSrflx = false;\n" +
            "    var completed = false;\n" +
            "    var cbs = [];\n" +
            "    var origAddEL = pc.addEventListener.bind(pc);\n" +
            "    pc.addEventListener = function(type, fn, opts) {\n" +
            "      if (type === 'icecandidate' && typeof fn === 'function') cbs.push(fn);\n" +
            "      return origAddEL(type, fn, opts);\n" +
            "    };\n" +
            "    function emitCandidate(candObj) {\n" +
            "      var ev = { type: 'icecandidate', candidate: candObj, target: pc, currentTarget: pc };\n" +
            "      if (typeof pc.onicecandidate === 'function') { try { pc.onicecandidate(ev); } catch(e){} }\n" +
            "      for (var i = 0; i < cbs.length; i++) { try { cbs[i].call(pc, ev); } catch(e){} }\n" +
            "    }\n" +
            "    origAddEL('icecandidate', function(e) {\n" +
            "      if (e && e.candidate && e.candidate.candidate && e.candidate.candidate.indexOf('typ srflx') !== -1) {\n" +
            "        deliveredSrflx = true;\n" +
            "        setTimeout(function() {\n" +
            "          if (!completed) { completed = true; emitCandidate(null); }\n" +
            "        }, 80);\n" +
            "      }\n" +
            "    });\n" +
            "    var origSLD = pc.setLocalDescription.bind(pc);\n" +
            "    pc.setLocalDescription = function(desc) {\n" +
            "      var p = origSLD(desc);\n" +
            "      if (TUNNEL_ON && SPOOF_IP && /^\\d+\\.\\d+\\.\\d+\\.\\d+$/.test(SPOOF_IP)) {\n" +
            "        setTimeout(function() {\n" +
            "          if (!deliveredSrflx) {\n" +
            "            deliveredSrflx = true;\n" +
            "            var candStr = 'candidate:842163049 1 udp 1677729535 ' + SPOOF_IP + ' 54321 typ srflx raddr 0.0.0.0 rport 0 generation 0';\n" +
            "            var cand = { candidate: candStr, sdpMid: '0', sdpMLineIndex: 0, address: SPOOF_IP, port: 54321, protocol: 'udp', type: 'srflx', toJSON: function(){ return { candidate: candStr, sdpMid: '0', sdpMLineIndex: 0 }; } };\n" +
            "            try { cand = new RTCIceCandidate({ candidate: candStr, sdpMid: '0', sdpMLineIndex: 0 }); } catch(e){}\n" +
            "            emitCandidate(cand);\n" +
            "            setTimeout(function() { if (!completed) { completed = true; emitCandidate(null); } }, 60);\n" +
            "          }\n" +
            "        }, 120);\n" +
            "      }\n" +
            "      return p;\n" +
            "    };\n" +
            "    return pc;\n" +
            "  }\n" +
            "  WrappedPC.prototype = OrigPC.prototype;\n" +
            "  try {\n" +
            "    Object.defineProperty(WrappedPC.prototype, 'localDescription', {\n" +
            "      get: function() {\n" +
            "        var d = Object.getOwnPropertyDescriptor(OrigPC.prototype, 'localDescription');\n" +
            "        var val = d && d.get ? d.get.call(this) : null;\n" +
            "        if (val && val.sdp && TUNNEL_ON && SPOOF_IP && val.sdp.indexOf(SPOOF_IP) === -1) {\n" +
            "          var sdp = val.sdp.replace(/c=IN IP4 [0-9.]+/g, 'c=IN IP4 ' + SPOOF_IP);\n" +
            "          sdp += 'a=candidate:842163049 1 udp 1677729535 ' + SPOOF_IP + ' 54321 typ srflx raddr 0.0.0.0 rport 0 generation 0\\r\\n';\n" +
            "          return { type: val.type, sdp: sdp, toJSON: function(){ return { type: val.type, sdp: sdp }; } };\n" +
            "        }\n" +
            "        return val;\n" +
            "      }\n" +
            "    });\n" +
            "  } catch(e) {}\n" +
            "  window.RTCPeerConnection = WrappedPC;\n" +
            "  window.webkitRTCPeerConnection = WrappedPC;\n" +
            "})();";
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
     * inside rootLayout's safe area, and immediately runs a fresh test.
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
        checkerWebView.setBackgroundColor(Color.parseColor("#0B0F19"));
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

        final String startScript = buildCheckerDocumentStartScript();
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(checkerWebView, startScript, Collections.singleton("*"));
            }
        } catch (Exception ignored) {}

        checkerWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (request != null) {
                    try {
                        request.grant(request.getResources());
                    } catch (Exception ignored) {}
                }
            }
        });

        checkerWebView.setWebViewClient(new WebViewClient() {
            private boolean retriedOnce = false;

            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                super.onPageStarted(view, pageUrl, favicon);
                if (view != null) {
                    view.evaluateJavascript(startScript, null);
                }
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
                RootBridge.refreshStatusFromRootNow();
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
