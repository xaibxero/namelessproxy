package com.nameless.proxy.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProxyConfig {
    public String profileName = "Default Slot";
    public String host = "";
    public int port = 1080;
    public String type = "socks5"; // "socks5" or "http"
    public String username = "";
    public String password = "";
    public String dnsMode = "auto"; // "auto", "cloudflare", "google", "quad9"
    public String appMode = "global"; // "global" or "per_app"
    public List<String> selectedApps = new ArrayList<>();
    public boolean hotspotShare = true;
    public boolean killSwitch = false;
    public boolean autoStartOnBoot = false;

    public static ProxyConfig fromJson(String jsonStr) {
        ProxyConfig cfg = new ProxyConfig();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return cfg;
        }
        try {
            JSONObject obj = new JSONObject(jsonStr);
            cfg.profileName = obj.optString("profileName", "Default Slot");
            cfg.host = obj.optString("host", "").trim();
            cfg.port = obj.optInt("port", 1080);
            String t = obj.optString("type", "socks5").trim().toLowerCase();
            cfg.type = (t.startsWith("http")) ? "http" : "socks5";
            cfg.username = obj.optString("username", obj.optString("user", "")).trim();
            cfg.password = obj.optString("password", obj.optString("pass", "")).trim();
            cfg.dnsMode = obj.optString("dnsMode", "auto").trim();
            cfg.appMode = obj.optString("appMode", "global").trim();
            cfg.hotspotShare = obj.optBoolean("hotspotShare", true);
            cfg.killSwitch = obj.optBoolean("killSwitch", false);
            cfg.autoStartOnBoot = obj.optBoolean("autoStartOnBoot", false);

            JSONArray arr = obj.optJSONArray("selectedApps");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String pkg = arr.optString(i, "").trim();
                    if (!pkg.isEmpty() && !cfg.selectedApps.contains(pkg)) {
                        cfg.selectedApps.add(pkg);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return cfg;
    }

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("profileName", profileName);
            obj.put("host", host);
            obj.put("port", port);
            obj.put("type", type);
            obj.put("username", username);
            obj.put("password", password);
            obj.put("dnsMode", dnsMode);
            obj.put("appMode", appMode);
            obj.put("hotspotShare", hotspotShare);
            obj.put("killSwitch", killSwitch);
            obj.put("autoStartOnBoot", autoStartOnBoot);
            JSONArray arr = new JSONArray();
            for (String pkg : selectedApps) {
                arr.put(pkg);
            }
            obj.put("selectedApps", arr);
        } catch (Exception ignored) {
        }
        return obj;
    }
}
