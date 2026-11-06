package com.nameless.proxy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.nameless.proxy.core.ProxyConfig;
import com.nameless.proxy.core.RootBridge;
import com.nameless.proxy.service.ProxyVpnService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView webView;
    private SharedPreferences prefs;
    private final ExecutorService bgExecutor = Executors.newCachedThreadPool();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.parseColor("#070A14"));
            window.setNavigationBarColor(Color.parseColor("#070A14"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        prefs = getSharedPreferences("nameless_prefs", Context.MODE_PRIVATE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#070A14"));
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/ui/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void notifyWebUi(final String jsCode) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(jsCode, null);
            }
        });
    }

    public class Bridge {

        @JavascriptInterface
        public String getInitialState() {
            try {
                JSONObject out = new JSONObject();
                String cfgStr = prefs.getString("active_config", "");
                ProxyConfig cfg = ProxyConfig.fromJson(cfgStr);
                out.put("config", cfg.toJsonObject());
                out.put("slots", new JSONArray(prefs.getString("saved_slots", "[]")));
                out.put("activeSlotIndex", prefs.getInt("active_slot_idx", 0));
                out.put("status", RootBridge.getStatusJson(getApplicationContext(), cfg));
                return out.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getStatus() {
            String cfgStr = prefs.getString("active_config", "");
            ProxyConfig cfg = ProxyConfig.fromJson(cfgStr);
            return RootBridge.getStatusJson(getApplicationContext(), cfg).toString();
        }

        @JavascriptInterface
        public void saveConfig(String jsonStr) {
            prefs.edit().putString("active_config", jsonStr != null ? jsonStr : "").apply();
        }

        @JavascriptInterface
        public void saveSlots(String slotsArrayJson, int activeIdx) {
            prefs.edit()
                    .putString("saved_slots", slotsArrayJson != null ? slotsArrayJson : "[]")
                    .putInt("active_slot_idx", activeIdx)
                    .apply();
        }

        @JavascriptInterface
        public void startProxy(String configJson) {
            final ProxyConfig cfg = ProxyConfig.fromJson(configJson);
            prefs.edit().putString("active_config", cfg.toJsonObject().toString()).apply();

            RootBridge.setConnectingState(true, "Starting tunnel engine...");
            notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");

            bgExecutor.execute(() -> {
                boolean ok = RootBridge.startTunnel(getApplicationContext(), cfg);
                if (ok) {
                    Intent svc = new Intent(MainActivity.this, ProxyVpnService.class);
                    svc.setAction(ProxyVpnService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc);
                    } else {
                        startService(svc);
                    }
                }
                notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");
            });
        }

        @JavascriptInterface
        public void stopProxy() {
            RootBridge.setConnectingState(false, "Disconnecting...");
            notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");

            bgExecutor.execute(() -> {
                RootBridge.stopTunnel(getApplicationContext());
                Intent svc = new Intent(MainActivity.this, ProxyVpnService.class);
                svc.setAction(ProxyVpnService.ACTION_STOP);
                stopService(svc);
                notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");
            });
        }

        @JavascriptInterface
        public void reloadAppsLive(String configJson) {
            final ProxyConfig cfg = ProxyConfig.fromJson(configJson);
            prefs.edit().putString("active_config", cfg.toJsonObject().toString()).apply();
            bgExecutor.execute(() -> {
                RootBridge.reloadAppRulesLive(getApplicationContext(), cfg);
                notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");
            });
        }

        @JavascriptInterface
        public void refreshExitMeta() {
            bgExecutor.execute(() -> {
                RootBridge.queryAndSyncExitMetadata();
                notifyWebUi("window.onNativeStatusUpdate && window.onNativeStatusUpdate()");
            });
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> pkgs = pm.getInstalledPackages(0);
                String selfPkg = getPackageName();
                List<JSONObject> userApps = new ArrayList<>();

                for (PackageInfo pi : pkgs) {
                    if (pi == null || pi.applicationInfo == null) continue;
                    String pkgName = pi.packageName;
                    if (selfPkg.equals(pkgName)) continue;

                    ApplicationInfo ai = pi.applicationInfo;
                    boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;

                    // Keep all user-installed apps plus essential browsers/messengers even if preloaded
                    boolean isBrowserOrImportant = pkgName.contains("chrome")
                            || pkgName.contains("browser")
                            || pkgName.contains("firefox")
                            || pkgName.contains("brave")
                            || pkgName.contains("opera")
                            || pkgName.contains("edge")
                            || pkgName.contains("youtube")
                            || pkgName.contains("vending")
                            || pkgName.contains("gms");

                    if (isSystem && !isBrowserOrImportant && pm.getLaunchIntentForPackage(pkgName) == null) {
                        continue;
                    }

                    CharSequence labelCs = ai.loadLabel(pm);
                    String label = labelCs != null ? labelCs.toString().trim() : pkgName;

                    JSONObject item = new JSONObject();
                    item.put("name", label);
                    item.put("pkg", pkgName);
                    item.put("uid", ai.uid);
                    item.put("system", isSystem);
                    userApps.add(item);
                }

                Collections.sort(userApps, Comparator.comparing(o -> o.optString("name", "").toLowerCase()));
                JSONArray arr = new JSONArray();
                for (JSONObject item : userApps) {
                    arr.put(item);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
    }
}
