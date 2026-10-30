package com.nameless.proxy.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RootBridge {

    public static final String ADB_DIR = "/data/adb/nameless_proxy";
    public static final String SINGBOX_BIN = ADB_DIR + "/sing-box";
    public static final String STUN_BIN = ADB_DIR + "/stun-responder";
    public static final String TUNNEL_SH = ADB_DIR + "/tunnel.sh";
    public static final String CONFIG_PATH = ADB_DIR + "/config.json";
    public static final String EXIT_JSON_PATH = ADB_DIR + "/exit_info.json";
    public static final String ROOT_AUTO_BACKUP = ADB_DIR + "/nameless_auto_backup.json";
    public static final String PUBLIC_BACKUP = "/sdcard/Download/nameless_proxy_backup.json";

    private static volatile boolean cachedRunning = false;
    private static volatile String cachedPid = "";
    private static volatile int cachedSlot = 0;
    private static volatile String cachedExitIp = "";
    private static volatile JSONObject cachedGeo = null;
    private static volatile String cachedAppsJson = null;
    private static boolean pollerStarted = false;

    private static final ScheduledExecutorService statusPoller = Executors.newSingleThreadScheduledExecutor();
    // Single-threaded serialized root writer so rapid edits never collide or corrupt files in /data/adb/nameless_proxy
    private static final ExecutorService diskSaveExecutor = Executors.newSingleThreadExecutor();

    public static synchronized void startBackgroundPoller() {
        if (pollerStarted) return;
        pollerStarted = true;
        statusPoller.scheduleWithFixedDelay(RootBridge::refreshStatusFromRootNow, 0, 2000, TimeUnit.MILLISECONDS);
    }

    public static void scheduleSaveSlots(Context context, String jsonStr) {
        final Context appCtx = context.getApplicationContext();
        // Save immediately to app SharedPreferences on caller thread so UI state is never lost
        SharedPreferences sp = appCtx.getSharedPreferences("nameless_v2_prefs", Context.MODE_PRIVATE);
        sp.edit().putString("slots_json", jsonStr).apply();
        // Queue serialized root write to /data/adb/nameless_proxy/
        diskSaveExecutor.execute(() -> saveAllSlotsToDisk(appCtx, jsonStr));
    }

    public static void refreshStatusFromRootNow() {
        try {
            String out = execSu(
                "/system/bin/sh " + TUNNEL_SH + " status",
                "if [ -f " + EXIT_JSON_PATH + " ]; then echo 'GEO:'$(cat " + EXIT_JSON_PATH + "); fi"
            );
            boolean running = false;
            String pid = "";
            int slot = 0;
            String exitIp = "";
            JSONObject geo = null;

            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.startsWith("RUNNING|")) {
                    running = true;
                    String[] parts = line.split("\\|");
                    if (parts.length > 1) pid = parts[1];
                    if (parts.length > 2 && parts[2].contains(":")) {
                        String[] us = parts[2].split(":");
                        if (us.length > 1) {
                            try { slot = Integer.parseInt(us[1]); } catch (Exception ignored) {}
                        }
                    }
                    if (parts.length > 3) exitIp = parts[3];
                } else if (line.startsWith("GEO:")) {
                    try {
                        geo = new JSONObject(line.substring(4).trim());
                    } catch (Exception ignored) {}
                }
            }
            cachedRunning = running;
            cachedPid = pid;
            cachedSlot = slot;
            cachedExitIp = exitIp;
            cachedGeo = geo;
        } catch (Exception ignored) {}
    }

    public static int getAndroidUserId() {
        return Process.myUid() / 100000;
    }

    public static String execSu(String... commands) {
        try {
            java.lang.Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                for (String cmd : commands) {
                    os.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
                }
                os.write("exit 0\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean checkRoot() {
        String out = execSu("id");
        return out.contains("uid=0");
    }

    public static boolean extractAssetToAdb(Context context, String assetName, String targetPath, String mode) {
        try (InputStream in = context.getAssets().open(assetName)) {
            java.lang.Process p = new ProcessBuilder(
                "su", "-c",
                "mkdir -p " + ADB_DIR + " && rm -f " + targetPath + " && cat > " + targetPath +
                " && chmod " + mode + " " + targetPath + " && chcon u:object_r:system_file:s0 " + targetPath + " 2>/dev/null || true"
            ).redirectErrorStream(true).start();
            try (OutputStream out = p.getOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean ensureBinariesExtracted(Context context) {
        extractAssetToAdb(context, "tunnel.sh", TUNNEL_SH, "755");
        extractAssetToAdb(context, "stun-responder", STUN_BIN, "755");
        String check = execSu(
            "if [ -x " + SINGBOX_BIN + " ]; then " + SINGBOX_BIN + " version 2>&1; else echo 'MISSING'; fi"
        );
        if (!check.contains("sing-box version")) {
            extractAssetToAdb(context, "sing-box", SINGBOX_BIN, "755");
            String recheck = execSu("if [ -x " + SINGBOX_BIN + " ]; then " + SINGBOX_BIN + " version 2>&1; fi");
            return recheck.contains("sing-box version");
        }
        return true;
    }

    public static boolean writeTextToAdb(String content, String targetPath) {
        try {
            java.lang.Process p = new ProcessBuilder(
                "su", "-c",
                "mkdir -p " + ADB_DIR + " && rm -f " + targetPath + " && cat > " + targetPath + " && chmod 644 " + targetPath
            ).redirectErrorStream(true).start();
            try (OutputStream out = p.getOutputStream()) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String cleanHost(String raw) {
        if (raw == null) return "";
        String h = raw.trim();
        if (h.startsWith("[")) h = h.substring(1);
        if (h.endsWith("]")) h = h.substring(0, h.length() - 1);
        return h.trim();
    }

    public static String resolveServerIp(String host) {
        String cleaned = cleanHost(host);
        if (cleaned.isEmpty()) return "";
        if (cleaned.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$") || cleaned.contains(":")) {
            return cleaned;
        }
        try {
            InetAddress addr = InetAddress.getByName(cleaned);
            return addr.getHostAddress();
        } catch (Exception e) {
            return cleaned;
        }
    }

    public static String getInstalledAppsJson(Context context) {
        if (cachedAppsJson != null) return cachedAppsJson;
        JSONArray arr = new JSONArray();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<JSONObject> items = new ArrayList<>();
            for (ApplicationInfo app : apps) {
                String pkg = app.packageName != null ? app.packageName : "";
                boolean isUserApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                boolean isBrowserOrMedia = pkg.contains("chrome") || pkg.contains("youtube")
                    || pkg.contains("brave") || pkg.contains("firefox") || pkg.contains("edge")
                    || pkg.contains("opera") || pkg.contains("vending");
                if (isUserApp || isBrowserOrMedia) {
                    JSONObject o = new JSONObject();
                    String label = pm.getApplicationLabel(app).toString();
                    o.put("name", label);
                    o.put("pkg", pkg);
                    o.put("uid", app.uid);
                    items.add(o);
                }
            }
            Collections.sort(items, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optString("name", "").compareToIgnoreCase(b.optString("name", ""));
                }
            });
            for (JSONObject item : items) {
                arr.put(item);
            }
            cachedAppsJson = arr.toString();
        } catch (Exception ignored) {}
        return arr.toString();
    }

    public static String generateSingBoxConfig(JSONObject cfg, String resolvedHost) throws Exception {
        String type = cfg.optString("type", "SOCKS5").toUpperCase();
        String rawHost = cleanHost(cfg.optString("host", ""));
        String serverHost = (resolvedHost != null && !resolvedHost.isEmpty()) ? resolvedHost : rawHost;
        int port = cfg.optInt("port", 1080);
        String username = cfg.optString("username", "").trim();
        String password = cfg.optString("password", "").trim();
        String sni = cfg.optString("sni", "").trim();
        String ssMethod = cfg.optString("ssMethod", "chacha20-ietf-poly1305").trim();
        String rpk = cfg.optString("realityPublicKey", "").trim();
        String rsid = cfg.optString("realityShortId", "").trim();
        boolean isIpv6 = serverHost.contains(":");

        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "info").put("timestamp", true));
        root.put("experimental", new JSONObject().put("cache_file", new JSONObject()
            .put("enabled", true)
            .put("path", ADB_DIR + "/cache.db")
            .put("store_fakeip", true)
        ));

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put(new JSONObject()
            .put("type", "fakeip")
            .put("tag", "dns-fakeip")
            .put("inet4_range", "198.18.0.0/15")
            .put("inet6_range", "fc00::/18")
        );
        servers.put(new JSONObject()
            .put("tag", "dns-remote")
            .put("type", "tcp")
            .put("server", isIpv6 ? "2606:4700:4700::1111" : "1.1.1.1")
            .put("server_port", 53)
            .put("detour", "proxy-out")
        );
        servers.put(new JSONObject()
            .put("tag", "dns-direct")
            .put("type", "udp")
            .put("server", isIpv6 ? "2620:fe::fe" : "9.9.9.9")
            .put("server_port", 53)
        );
        dns.put("servers", servers);

        JSONArray dnsRules = new JSONArray();
        JSONArray blockTypes = new JSONArray().put("HTTPS").put("SVCB");
        if (!isIpv6) blockTypes.put("AAAA");
        else blockTypes.put("A");
        dnsRules.put(new JSONObject()
            .put("query_type", blockTypes)
            .put("action", "predefined")
            .put("rcode", "NOERROR")
        );
        dnsRules.put(new JSONObject()
            .put("domain", new JSONArray().put("use-application-dns.net"))
            .put("action", "predefined")
            .put("rcode", "NXDOMAIN")
        );
        dnsRules.put(new JSONObject()
            .put("query_type", new JSONArray().put(isIpv6 ? "AAAA" : "A"))
            .put("action", "route")
            .put("server", "dns-fakeip")
            .put("rewrite_ttl", 25)
        );
        dns.put("rules", dnsRules);
        dns.put("strategy", isIpv6 ? "ipv6_only" : "ipv4_only");
        dns.put("final", "dns-remote");
        dns.put("reverse_mapping", true);
        root.put("dns", dns);

        JSONArray inbounds = new JSONArray();
        inbounds.put(new JSONObject()
            .put("type", "redirect")
            .put("tag", "redirect-in")
            .put("listen", "127.0.0.1")
            .put("listen_port", 10800)
        );
        inbounds.put(new JSONObject()
            .put("type", "mixed")
            .put("tag", "internal-socks-in")
            .put("listen", "127.0.0.1")
            .put("listen_port", 10801)
        );
        inbounds.put(new JSONObject()
            .put("type", "direct")
            .put("tag", "dns-in")
            .put("listen", "127.0.0.1")
            .put("listen_port", 10802)
            .put("network", new JSONArray().put("tcp").put("udp"))
            .put("override_address", "1.1.1.1")
            .put("override_port", 53)
        );
        inbounds.put(new JSONObject()
            .put("type", "direct")
            .put("tag", "dns-hs-in")
            .put("listen", "0.0.0.0")
            .put("listen_port", 10804)
            .put("network", new JSONArray().put("tcp").put("udp"))
            .put("override_address", "1.1.1.1")
            .put("override_port", 53)
        );
        inbounds.put(new JSONObject()
            .put("type", "redirect")
            .put("tag", "redirect-hs-in")
            .put("listen", "0.0.0.0")
            .put("listen_port", 10806)
        );
        inbounds.put(new JSONObject()
            .put("type", "direct")
            .put("tag", "dns-v6-in")
            .put("listen", "::1")
            .put("listen_port", 10807)
            .put("network", new JSONArray().put("tcp").put("udp"))
            .put("override_address", "1.1.1.1")
            .put("override_port", 53)
        );
        root.put("inbounds", inbounds);

        JSONArray outbounds = new JSONArray();
        JSONObject proxyOut = new JSONObject().put("tag", "proxy-out");
        switch (type) {
            case "SOCKS4":
                proxyOut.put("type", "socks").put("server", serverHost).put("server_port", port).put("version", "4");
                if (!username.isEmpty()) proxyOut.put("username", username);
                break;
            case "HTTP":
                proxyOut.put("type", "http").put("server", serverHost).put("server_port", port);
                if (!username.isEmpty()) proxyOut.put("username", username).put("password", password);
                if (!sni.isEmpty()) proxyOut.put("tls", new JSONObject().put("enabled", true).put("server_name", sni));
                break;
            case "SHADOWSOCKS":
                proxyOut.put("type", "shadowsocks").put("server", serverHost).put("server_port", port)
                    .put("method", ssMethod.isEmpty() ? "chacha20-ietf-poly1305" : ssMethod)
                    .put("password", password);
                break;
            case "VLESS": {
                proxyOut.put("type", "vless").put("server", serverHost).put("server_port", port)
                    .put("uuid", password.isEmpty() ? username : password)
                    .put("packet_encoding", "xudp");
                if (!rpk.isEmpty()) proxyOut.put("flow", "xtls-rprx-vision");
                JSONObject tls = new JSONObject()
                    .put("enabled", true)
                    .put("server_name", sni.isEmpty() ? rawHost : sni)
                    .put("utls", new JSONObject().put("enabled", true).put("fingerprint", "chrome"));
                if (!rpk.isEmpty()) {
                    tls.put("reality", new JSONObject().put("enabled", true).put("public_key", rpk).put("short_id", rsid));
                }
                proxyOut.put("tls", tls);
                break;
            }
            case "VMESS":
                proxyOut.put("type", "vmess").put("server", serverHost).put("server_port", port)
                    .put("uuid", password.isEmpty() ? username : password)
                    .put("security", "auto").put("alter_id", 0);
                if (!sni.isEmpty() || port == 443) {
                    proxyOut.put("tls", new JSONObject().put("enabled", true).put("server_name", sni.isEmpty() ? rawHost : sni));
                }
                break;
            case "TROJAN":
                proxyOut.put("type", "trojan").put("server", serverHost).put("server_port", port)
                    .put("password", password.isEmpty() ? username : password)
                    .put("tls", new JSONObject()
                        .put("enabled", true)
                        .put("server_name", sni.isEmpty() ? rawHost : sni)
                        .put("utls", new JSONObject().put("enabled", true).put("fingerprint", "chrome")));
                break;
            case "HYSTERIA2":
                proxyOut.put("type", "hysteria2").put("server", serverHost).put("server_port", port)
                    .put("password", password.isEmpty() ? username : password)
                    .put("tls", new JSONObject().put("enabled", true).put("server_name", sni.isEmpty() ? rawHost : sni));
                break;
            case "TUIC":
                proxyOut.put("type", "tuic").put("server", serverHost).put("server_port", port)
                    .put("uuid", username.isEmpty() ? password : username)
                    .put("password", password)
                    .put("congestion_control", "bbr")
                    .put("tls", new JSONObject().put("enabled", true).put("server_name", sni.isEmpty() ? rawHost : sni));
                break;
            default:
                proxyOut.put("type", "socks").put("server", serverHost).put("server_port", port).put("version", "5");
                if (!username.isEmpty()) proxyOut.put("username", username).put("password", password);
                break;
        }
        outbounds.put(proxyOut);
        outbounds.put(new JSONObject().put("type", "direct").put("tag", "direct-out"));
        root.put("outbounds", outbounds);

        JSONObject route = new JSONObject()
            .put("auto_detect_interface", false)
            .put("default_domain_resolver", "dns-direct")
            .put("final", "proxy-out");
        JSONArray routeRules = new JSONArray();
        routeRules.put(new JSONObject()
            .put("inbound", new JSONArray().put("dns-in").put("dns-hs-in").put("dns-v6-in"))
            .put("action", "hijack-dns")
        );
        routeRules.put(new JSONObject().put("port", new JSONArray().put(53)).put("action", "hijack-dns"));
        routeRules.put(new JSONObject().put("action", "sniff").put("timeout", "200ms"));
        routeRules.put(new JSONObject()
            .put("network", "udp")
            .put("port", new JSONArray().put(80).put(443).put(853))
            .put("action", "reject")
        );
        route.put("rules", routeRules);
        root.put("route", route);

        return root.toString(2);
    }

    public static JSONObject startTunnel(Context context, JSONObject cfg) {
        JSONObject res = new JSONObject();
        try {
            if (!ensureBinariesExtracted(context)) {
                return res.put("ok", false).put("error", "Failed to extract binaries");
            }
            String rawHost = cleanHost(cfg.optString("host", ""));
            if (rawHost.isEmpty()) {
                return res.put("ok", false).put("error", "Proxy host is empty");
            }
            String resolvedIp = resolveServerIp(rawHost);
            String sbJson = generateSingBoxConfig(cfg, resolvedIp);
            int userId = getAndroidUserId();
            int slot = cfg.optInt("slot", 0);

            writeTextToAdb(sbJson, CONFIG_PATH);
            writeTextToAdb(sbJson, ADB_DIR + "/config_u" + userId + "_s" + slot + ".json");

            int routeHs = cfg.optBoolean("routeHotspot", true) ? 1 : 0;
            String webrtcMode = cfg.optString("webrtcMode", "SPOOF");
            String targetUids = cfg.optString("targetUids", "ALL");
            if (!targetUids.equals("ALL") && !targetUids.isEmpty()) {
                int myUid = Process.myUid();
                targetUids = targetUids + "," + myUid;
            } else {
                targetUids = "ALL";
            }

            int appPid = Process.myPid();
            String out = execSu(
                "echo -800 > /proc/" + appPid + "/oom_score_adj 2>/dev/null || true",
                "echo " + appPid + " > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true",
                "cmd deviceidle whitelist +com.nameless.proxy >/dev/null 2>&1 || true",
                "/system/bin/sh " + TUNNEL_SH + " start \"" + resolvedIp + "\" \"" + userId + "\" \"" + routeHs + "\" \"" + slot + "\" \"" + targetUids + "\" \"" + webrtcMode + "\""
            );
            refreshStatusFromRootNow();
            if (out.contains("OK") || cachedRunning) {
                return res.put("ok", true).put("status", cachedPid);
            } else {
                return res.put("ok", false).put("error", out.isEmpty() ? "Tunnel failed to start" : out);
            }
        } catch (Exception e) {
            try {
                res.put("ok", false).put("error", e.getMessage());
            } catch (Exception ignored) {}
            return res;
        }
    }

    public static boolean stopTunnel(Context context) {
        ensureBinariesExtracted(context);
        execSu("/system/bin/sh " + TUNNEL_SH + " stop");
        cachedRunning = false;
        cachedPid = "";
        cachedExitIp = "";
        cachedGeo = null;
        return true;
    }

    public static boolean isTunnelRunning() {
        refreshStatusFromRootNow();
        return cachedRunning;
    }

    public static JSONObject getTunnelStatusJson() {
        JSONObject obj = new JSONObject();
        try {
            long[] bytes = readPhysicalNetworkBytes();
            obj.put("running", cachedRunning);
            obj.put("pid", cachedPid);
            obj.put("slot", cachedSlot);
            obj.put("exitIp", cachedExitIp);
            obj.put("rxBytes", bytes[0]);
            obj.put("txBytes", bytes[1]);
            if (cachedGeo != null) {
                obj.put("geo", cachedGeo);
            }
        } catch (Exception ignored) {}
        return obj;
    }

    public static long[] readPhysicalNetworkBytes() {
        long rx = 0L;
        long tx = 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(new File("/proc/net/dev")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String iface = line.substring(0, colon).trim();
                    if (iface.startsWith("wlan") || iface.startsWith("rmnet") || iface.startsWith("v4-rmnet")
                        || iface.startsWith("ccmni") || iface.startsWith("eth") || iface.startsWith("rndis")) {
                        String[] parts = line.substring(colon + 1).trim().split("\\s+");
                        if (parts.length >= 9) {
                            rx += Long.parseLong(parts[0]);
                            tx += Long.parseLong(parts[8]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        if (rx == 0L && tx == 0L) {
            rx = Math.max(0L, TrafficStats.getTotalRxBytes());
            tx = Math.max(0L, TrafficStats.getTotalTxBytes());
        }
        return new long[]{rx, tx};
    }

    public static int pingServer(String host, int port) {
        String cleaned = cleanHost(host);
        if (cleaned.isEmpty()) return -1;
        long start = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(cleaned, port), 3500);
            return (int) Math.max(1L, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getLogsOrAudit(boolean audit) {
        return execSu("/system/bin/sh " + TUNNEL_SH + (audit ? " audit" : " logs"));
    }

    public static void clearLogs() {
        execSu("/system/bin/sh " + TUNNEL_SH + " clear_logs");
    }

    public static JSONObject parseProxyUri(String rawUri) {
        JSONObject out = new JSONObject();
        try {
            String uriStr = rawUri.trim();
            if (uriStr.startsWith("vmess://")) {
                String b64 = uriStr.substring(8).trim();
                String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                JSONObject v = new JSONObject(decoded);
                out.put("type", "VMESS");
                out.put("host", v.optString("add", ""));
                out.put("port", v.optInt("port", 443));
                out.put("username", v.optString("id", ""));
                out.put("password", v.optString("id", ""));
                out.put("sni", v.optString("sni", v.optString("host", "")));
                return out;
            }
            if (!uriStr.contains("://") && uriStr.contains(":")) {
                String[] parts = uriStr.split(":");
                if (parts.length >= 2) {
                    out.put("type", "SOCKS5");
                    out.put("host", parts[0].trim());
                    out.put("port", Integer.parseInt(parts[1].trim()));
                    if (parts.length >= 3) out.put("username", parts[2].trim());
                    if (parts.length >= 4) out.put("password", parts[3].trim());
                    return out;
                }
            }
            URI u = new URI(uriStr);
            String scheme = u.getScheme() == null ? "socks5" : u.getScheme().toLowerCase();
            String type = "SOCKS5";
            if (scheme.contains("http")) type = "HTTP";
            else if (scheme.equals("socks4")) type = "SOCKS4";
            else if (scheme.equals("ss")) type = "SHADOWSOCKS";
            else if (scheme.equals("vless")) type = "VLESS";
            else if (scheme.equals("trojan")) type = "TROJAN";
            else if (scheme.contains("hy2") || scheme.contains("hysteria")) type = "HYSTERIA2";
            else if (scheme.equals("tuic")) type = "TUIC";

            out.put("type", type);
            out.put("host", u.getHost() != null ? u.getHost() : "");
            out.put("port", u.getPort() > 0 ? u.getPort() : 1080);

            String userInfo = u.getRawUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                String decodedUser = URLDecoder.decode(userInfo, "UTF-8");
                int idx = decodedUser.indexOf(':');
                if (idx >= 0) {
                    out.put("username", decodedUser.substring(0, idx));
                    out.put("password", decodedUser.substring(idx + 1));
                } else {
                    out.put("username", decodedUser);
                    out.put("password", decodedUser);
                }
            }
            String query = u.getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String k = kv[0].toLowerCase();
                        String v = URLDecoder.decode(kv[1], "UTF-8");
                        if (k.equals("sni") || k.equals("peer")) out.put("sni", v);
                        else if (k.equals("pbk")) out.put("realityPublicKey", v);
                        else if (k.equals("sid")) out.put("realityShortId", v);
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Immediately creates and saves all configuration & backup files inside /data/adb/nameless_proxy/:
     * 1. /data/adb/nameless_proxy/slots_u{userId}.json
     * 2. /data/adb/nameless_proxy/nameless_auto_backup.json
     * 3. /data/adb/nameless_proxy/backup_u{userId}_s{slot}.json (for every configured slot)
     * 4. /data/adb/nameless_proxy/config_u{userId}_s{slot}.json (sing-box config for every configured slot)
     * 5. /data/adb/nameless_proxy/config.json (active slot sing-box config)
     */
    public static boolean saveAllSlotsToDisk(Context context, String jsonStr) {
        try {
            SharedPreferences sp = context.getSharedPreferences("nameless_v2_prefs", Context.MODE_PRIVATE);
            sp.edit().putString("slots_json", jsonStr).apply();

            int userId = getAndroidUserId();
            JSONObject parsed = new JSONObject(jsonStr);
            int activeSlot = parsed.optInt("activeSlot", 0);
            boolean routeHotspot = parsed.optBoolean("routeHotspot", true);
            boolean routeAllApps = parsed.optBoolean("routeAllApps", true);
            JSONArray slotsArr = parsed.optJSONArray("slots");

            // Write main slots file
            writeTextToAdb( parsed.toString(2), ADB_DIR + "/slots_u" + userId + ".json");

            // Also build v18-compatible nameless_auto_backup.json and per-slot backup_u0_sX.json + config_u0_sX.json
            JSONObject autoBackup = new JSONObject();
            autoBackup.put("version", 3);
            autoBackup.put("boot_slot", activeSlot);
            autoBackup.put("route_hotspot", routeHotspot);
            JSONArray compatSlots = new JSONArray();

            if (slotsArr != null) {
                for (int i = 0; i < slotsArr.length(); i++) {
                    JSONObject s = slotsArr.optJSONObject(i);
                    if (s == null) continue;
                    String host = cleanHost(s.optString("host", ""));
                    int port = s.optInt("port", 1080);
                    String type = s.optString("type", "SOCKS5");
                    String user = s.optString("username", "");
                    String pass = s.optString("password", "");
                    String sni = s.optString("sni", "");
                    String ssMethod = s.optString("ssMethod", "chacha20-ietf-poly1305");
                    String rpk = s.optString("realityPublicKey", "");
                    String rsid = s.optString("realityShortId", "");

                    JSONObject compatSlot = new JSONObject();
                    compatSlot.put("slot", i);
                    compatSlot.put("proxy_type", type);
                    compatSlot.put("host", host);
                    compatSlot.put("port", String.valueOf(port));
                    compatSlot.put("username", user);
                    compatSlot.put("password", pass);
                    compatSlot.put("sni", sni);
                    compatSlot.put("ss_method", ssMethod);
                    compatSlot.put("reality_pk", rpk);
                    compatSlot.put("reality_sid", rsid);
                    compatSlot.put("route_hotspot", routeHotspot);
                    compatSlot.put("route_whole_profile", routeAllApps);
                    compatSlots.put(compatSlot);

                    // Save per-slot backup file and pre-generated sing-box config file whenever host is entered
                    if (!host.isEmpty()) {
                        writeTextToAdb(compatSlot.toString(2), ADB_DIR + "/backup_u" + userId + "_s" + i + ".json");
                        String sbCfg = generateSingBoxConfig(s, host);
                        writeTextToAdb(sbCfg, ADB_DIR + "/config_u" + userId + "_s" + i + ".json");
                        if (i == activeSlot) {
                            writeTextToAdb(sbCfg, CONFIG_PATH);
                        }
                    }
                }
            }
            autoBackup.put("slots", compatSlots);
            writeTextToAdb(autoBackup.toString(2), ROOT_AUTO_BACKUP);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String loadAllSlotsFromDisk(Context context) {
        SharedPreferences sp = context.getSharedPreferences("nameless_v2_prefs", Context.MODE_PRIVATE);
        String saved = sp.getString("slots_json", "");
        if (saved != null && !saved.isEmpty() && saved.contains("\"host\"")) {
            return saved;
        }
        int userId = getAndroidUserId();
        String fromAdb = execSu("if [ -f " + ADB_DIR + "/slots_u" + userId + ".json ]; then cat " + ADB_DIR + "/slots_u" + userId + ".json; fi");
        if (!fromAdb.isEmpty() && fromAdb.startsWith("{")) {
            sp.edit().putString("slots_json", fromAdb).apply();
            return fromAdb;
        }
        // Fallback: Check if nameless_auto_backup.json from v18 or earlier exists in /data/adb/nameless_proxy/
        String fromAuto = execSu("if [ -f " + ROOT_AUTO_BACKUP + " ]; then cat " + ROOT_AUTO_BACKUP + "; fi");
        if (!fromAuto.isEmpty() && fromAuto.contains("{")) {
            try {
                int idx = fromAuto.indexOf('{');
                JSONObject legacy = new JSONObject(fromAuto.substring(idx));
                JSONArray legacySlots = legacy.optJSONArray("slots");
                if (legacySlots != null && legacySlots.length() > 0) {
                    JSONObject converted = new JSONObject();
                    converted.put("activeSlot", legacy.optInt("boot_slot", 0));
                    converted.put("routeHotspot", legacy.optBoolean("route_hotspot", true));
                    converted.put("routeAllApps", true);
                    JSONArray newSlots = new JSONArray();
                    for (int i = 0; i < 5; i++) {
                        JSONObject ls = i < legacySlots.length() ? legacySlots.optJSONObject(i) : null;
                        JSONObject ns = new JSONObject();
                        if (ls != null) {
                            ns.put("type", ls.optString("proxy_type", "SOCKS5"));
                            ns.put("host", ls.optString("host", ""));
                            ns.put("port", Integer.parseInt(ls.optString("port", "1080")));
                            ns.put("username", ls.optString("username", ""));
                            ns.put("password", ls.optString("password", ""));
                            ns.put("sni", ls.optString("sni", ""));
                            ns.put("ssMethod", ls.optString("ss_method", "chacha20-ietf-poly1305"));
                            ns.put("realityPublicKey", ls.optString("reality_pk", ""));
                            ns.put("realityShortId", ls.optString("reality_sid", ""));
                        } else {
                            ns.put("type", "SOCKS5").put("host", "").put("port", 1080);
                        }
                        newSlots.put(ns);
                    }
                    converted.put("slots", newSlots);
                    String outStr = converted.toString();
                    sp.edit().putString("slots_json", outStr).apply();
                    return outStr;
                }
            } catch (Exception ignored) {}
        }
        return saved != null ? saved : "";
    }

    public static boolean exportBackup(Context context) {
        String data = loadAllSlotsFromDisk(context);
        if (data.isEmpty()) return false;
        saveAllSlotsToDisk(context, data);
        return writeTextToAdb(data, PUBLIC_BACKUP);
    }

    public static String importBackup(Context context) {
        String data = execSu(
            "if [ -f " + PUBLIC_BACKUP + " ]; then cat " + PUBLIC_BACKUP + "; " +
            "elif [ -f /sdcard/Download/nameless_backup.json ]; then cat /sdcard/Download/nameless_backup.json; fi"
        );
        if (!data.isEmpty() && data.contains("{")) {
            int idx = data.indexOf('{');
            String clean = data.substring(idx);
            saveAllSlotsToDisk(context, clean);
            return loadAllSlotsFromDisk(context);
        }
        return "";
    }
}
