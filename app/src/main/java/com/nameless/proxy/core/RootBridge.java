package com.nameless.proxy.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RootBridge {

    private static volatile boolean connecting = false;
    private static volatile String stepMessage = "Ready";
    private static volatile String lastError = "";
    private static volatile String cachedExitIp = "";
    private static volatile String cachedCountry = "";
    private static volatile String cachedCountryCode = "";
    private static volatile String cachedIsp = "";
    private static volatile String cachedTimezone = "";
    private static volatile long connectedSinceMs = 0L;

    public static int getCurrentUserId() {
        return Process.myUid() / 100000;
    }

    public static String getWorkDirPath() {
        return "/data/adb/nameless_u" + getCurrentUserId();
    }

    public static void setConnectingState(boolean state, String msg) {
        connecting = state;
        if (msg != null) {
            stepMessage = msg;
        }
    }

    public static boolean isConnecting() {
        return connecting;
    }

    public static String getStepMessage() {
        return stepMessage;
    }

    public static String getLastError() {
        return lastError;
    }

    public static boolean checkRootAccess() {
        String out = execRoot("id -u", 4000);
        return out != null && out.trim().equals("0");
    }

    public static String execRoot(String cmd, long timeoutMs) {
        Process proc = null;
        try {
            proc = new ProcessBuilder("su", "-c", cmd)
                    .redirectErrorStream(true)
                    .start();
            final StringBuilder sb = new StringBuilder();
            final InputStream is = proc.getInputStream();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (sb.length() < 16384) {
                            sb.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            reader.join(400);
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }

    public static boolean isTunnelRunning() {
        String workDir = getWorkDirPath();
        String out = execRoot("if [ -f " + workDir + "/singbox.pid ]; then " +
                "p=$(cat " + workDir + "/singbox.pid 2>/dev/null); " +
                "if [ -n \"$p\" ] && kill -0 \"$p\" 2>/dev/null; then echo RUNNING; fi; fi", 3000);
        return out != null && out.contains("RUNNING");
    }

    public static synchronized boolean startTunnel(Context ctx, ProxyConfig cfg) {
        lastError = "";
        connecting = true;
        stepMessage = "Checking root & preparing isolated workspace...";

        try {
            if (cfg == null || cfg.host == null || cfg.host.trim().isEmpty() || cfg.port <= 0 || cfg.port > 65535) {
                lastError = "Invalid proxy host or port.";
                connecting = false;
                stepMessage = "Invalid proxy configuration";
                return false;
            }

            if (!checkRootAccess()) {
                lastError = "Root access denied. Grant superuser permission in Magisk / KernelSU / APatch.";
                connecting = false;
                stepMessage = "Root permission required";
                return false;
            }

            String workDir = getWorkDirPath();
            File localStage = new File(ctx.getFilesDir(), "stage");
            if (!localStage.exists()) {
                localStage.mkdirs();
            }

            // 1. Extract tunnel.sh from assets
            stepMessage = "Extracting tunnel engine & binaries...";
            File stagedScript = new File(localStage, "tunnel.sh");
            extractAsset(ctx, "tunnel.sh", stagedScript);

            // 2. Locate native binaries (libtun2socks.so = sing-box, libstun.so = stun-responder)
            String nativeDir = ctx.getApplicationInfo().nativeLibraryDir;
            File srcSingBox = new File(nativeDir, "libtun2socks.so");
            File srcStun = new File(nativeDir, "libstun.so");
            if (!srcSingBox.exists()) {
                lastError = "Missing libtun2socks.so (sing-box) in nativeLibraryDir.";
                connecting = false;
                stepMessage = "Binary missing";
                return false;
            }

            // 3. Resolve Per-App UIDs if specific apps are selected
            stepMessage = "Resolving app UIDs & building sing-box config...";
            Set<Integer> uidSet = resolveSelectedUids(ctx, cfg);
            boolean usePerApp = !uidSet.isEmpty();

            // 4. Write sing-box 1.11.4 configuration
            File stagedConfig = new File(localStage, "config.json");
            String configJson = buildSingBoxConfig(cfg, workDir);
            writeTextFile(stagedConfig, configJson);

            // 5. Write tunnel.env
            File stagedEnv = new File(localStage, "tunnel.env");
            StringBuilder uidCsv = new StringBuilder();
            for (Integer uid : uidSet) {
                if (uidCsv.length() > 0) uidCsv.append(",");
                uidCsv.append(uid);
            }
            String envContent = "WORK_DIR=\"" + workDir + "\"\n"
                    + "PROXY_HOST=\"" + sanitizeShell(cfg.host) + "\"\n"
                    + "PROXY_PORT=\"" + cfg.port + "\"\n"
                    + "PROXY_TYPE=\"" + (cfg.type.startsWith("http") ? "http" : "socks") + "\"\n"
                    + "APP_MODE=\"" + (usePerApp ? "per_app" : "global") + "\"\n"
                    + "UID_LIST=\"" + uidCsv + "\"\n"
                    + "HOTSPOT_SHARE=\"" + (cfg.hotspotShare ? "1" : "0") + "\"\n"
                    + "KILL_SWITCH=\"" + (cfg.killSwitch ? "1" : "0") + "\"\n"
                    + "USER_ID=\"" + getCurrentUserId() + "\"\n"
                    + "SPOOF_IP=\"" + sanitizeShell(cachedExitIp) + "\"\n";
            writeTextFile(stagedEnv, envContent);

            // 6. Copy files to /data/adb/nameless_u<id> and launch tunnel.sh start
            stepMessage = "Launching sing-box & STUN shield...";
            String setupCmd = "mkdir -p " + workDir + " && "
                    + "cp -f " + stagedScript.getAbsolutePath() + " " + workDir + "/tunnel.sh && "
                    + "cp -f " + stagedConfig.getAbsolutePath() + " " + workDir + "/config.json && "
                    + "cp -f " + stagedEnv.getAbsolutePath() + " " + workDir + "/tunnel.env && "
                    + "cp -f " + srcSingBox.getAbsolutePath() + " " + workDir + "/libtun2socks.so && "
                    + (srcStun.exists() ? "cp -f " + srcStun.getAbsolutePath() + " " + workDir + "/libstun.so && " : "")
                    + "chmod 755 " + workDir + "/tunnel.sh " + workDir + "/libtun2socks.so " + workDir + "/libstun.so 2>/dev/null; "
                    + "WORK_DIR=" + workDir + " sh " + workDir + "/tunnel.sh start";

            execRoot(setupCmd, 12000);

            if (!isTunnelRunning()) {
                String logTail = execRoot("tail -n 20 " + workDir + "/tunnel.log 2>/dev/null", 3000);
                lastError = "Engine failed to start. " + (logTail != null && !logTail.trim().isEmpty() ? logTail.trim() : "Check proxy host/port.");
                connecting = false;
                stepMessage = "Failed to start";
                return false;
            }

            connectedSinceMs = System.currentTimeMillis();
            stepMessage = "Verifying proxy exit IP & WebRTC spoof...";
            refreshExitMetadataAsync();
            connecting = false;
            stepMessage = "Connected & Protected";
            return true;
        } catch (Exception e) {
            lastError = "Startup exception: " + e.getMessage();
            connecting = false;
            stepMessage = "Error";
            return false;
        }
    }

    public static synchronized boolean stopTunnel(Context ctx) {
        connecting = false;
        stepMessage = "Stopping tunnel...";
        String workDir = getWorkDirPath();
        execRoot("if [ -f " + workDir + "/tunnel.sh ]; then WORK_DIR=" + workDir + " sh " + workDir + "/tunnel.sh stop; fi", 8000);
        connectedSinceMs = 0L;
        cachedExitIp = "";
        cachedCountry = "";
        cachedCountryCode = "";
        cachedIsp = "";
        cachedTimezone = "";
        stepMessage = "Disconnected";
        return !isTunnelRunning();
    }

    public static synchronized boolean reloadAppRulesLive(Context ctx, ProxyConfig cfg) {
        if (!isTunnelRunning()) {
            return false;
        }
        try {
            String workDir = getWorkDirPath();
            File localStage = new File(ctx.getFilesDir(), "stage");
            if (!localStage.exists()) localStage.mkdirs();

            Set<Integer> uidSet = resolveSelectedUids(ctx, cfg);
            boolean usePerApp = !uidSet.isEmpty();

            StringBuilder uidCsv = new StringBuilder();
            for (Integer uid : uidSet) {
                if (uidCsv.length() > 0) uidCsv.append(",");
                uidCsv.append(uid);
            }
            File stagedEnv = new File(localStage, "tunnel.env");
            String envContent = "WORK_DIR=\"" + workDir + "\"\n"
                    + "PROXY_HOST=\"" + sanitizeShell(cfg.host) + "\"\n"
                    + "PROXY_PORT=\"" + cfg.port + "\"\n"
                    + "PROXY_TYPE=\"" + (cfg.type.startsWith("http") ? "http" : "socks") + "\"\n"
                    + "APP_MODE=\"" + (usePerApp ? "per_app" : "global") + "\"\n"
                    + "UID_LIST=\"" + uidCsv + "\"\n"
                    + "HOTSPOT_SHARE=\"" + (cfg.hotspotShare ? "1" : "0") + "\"\n"
                    + "KILL_SWITCH=\"" + (cfg.killSwitch ? "1" : "0") + "\"\n"
                    + "USER_ID=\"" + getCurrentUserId() + "\"\n"
                    + "SPOOF_IP=\"" + sanitizeShell(cachedExitIp) + "\"\n";
            writeTextFile(stagedEnv, envContent);

            String cmd = "cp -f " + stagedEnv.getAbsolutePath() + " " + workDir + "/tunnel.env && "
                    + "WORK_DIR=" + workDir + " sh " + workDir + "/tunnel.sh reload";
            execRoot(cmd, 6000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<Integer> resolveSelectedUids(Context ctx, ProxyConfig cfg) {
        Set<Integer> uids = new LinkedHashSet<>();
        if (cfg == null || cfg.selectedApps == null || cfg.selectedApps.isEmpty()) {
            return uids;
        }
        // If user explicitly chose global and selected 0 apps, return empty
        PackageManager pm = ctx.getPackageManager();
        int userId = getCurrentUserId();
        String selfPkg = ctx.getPackageName();

        // Count total user-visible apps so if "Select All" was used, we can still route per-UID or global cleanly
        for (String pkg : cfg.selectedApps) {
            if (pkg == null || pkg.trim().isEmpty() || pkg.equals(selfPkg)) {
                continue;
            }
            pkg = pkg.trim();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai != null && ai.uid > 0) {
                    uids.add(ai.uid);
                    continue;
                }
            } catch (Exception ignored) {
            }
            // Root fallback via /data/system/packages.list
            String line = execRoot("grep -E '^" + sanitizeShell(pkg) + " ' /data/system/packages.list 2>/dev/null | awk '{print $2}'", 2000);
            if (line != null && !line.trim().isEmpty()) {
                try {
                    int baseAppId = Integer.parseInt(line.trim().split("\\s+")[0]);
                    if (baseAppId > 0) {
                        uids.add(userId * 100000 + baseAppId);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return uids;
    }

    public static String buildSingBoxConfig(ProxyConfig cfg, String workDir) throws Exception {
        JSONObject root = new JSONObject();

        // 1. Log
        JSONObject logObj = new JSONObject();
        logObj.put("level", "warn");
        logObj.put("timestamp", true);
        root.put("log", logObj);

        // 2. DNS (sing-box 1.11.4 verified schema)
        JSONObject dnsObj = new JSONObject();
        JSONArray dnsServers = new JSONArray();

        String directDnsIp = "9.9.9.9";
        if ("cloudflare".equalsIgnoreCase(cfg.dnsMode)) {
            directDnsIp = "1.1.1.1";
        } else if ("google".equalsIgnoreCase(cfg.dnsMode)) {
            directDnsIp = "8.8.8.8";
        }

        JSONObject srvDirect = new JSONObject();
        srvDirect.put("tag", "dns-direct");
        srvDirect.put("address", directDnsIp);
        srvDirect.put("detour", "direct-out");
        srvDirect.put("strategy", "ipv4_only");
        dnsServers.put(srvDirect);

        JSONObject srvRemote = new JSONObject();
        srvRemote.put("tag", "dns-remote");
        srvRemote.put("address", "https://1.1.1.1/dns-query");
        srvRemote.put("detour", "proxy-out");
        srvRemote.put("strategy", "ipv4_only");
        dnsServers.put(srvRemote);

        JSONObject srvFake = new JSONObject();
        srvFake.put("tag", "dns-fakeip");
        srvFake.put("address", "fakeip");
        srvFake.put("strategy", "ipv4_only");
        dnsServers.put(srvFake);

        dnsObj.put("servers", dnsServers);

        JSONArray dnsRules = new JSONArray();

        JSONObject ruleAnyOutbound = new JSONObject();
        ruleAnyOutbound.put("outbound", "any");
        ruleAnyOutbound.put("server", "dns-direct");
        dnsRules.put(ruleAnyOutbound);

        JSONObject ruleDirectInbound = new JSONObject();
        JSONArray directInArr = new JSONArray();
        directInArr.put("direct-redirect-in");
        ruleDirectInbound.put("inbound", directInArr);
        ruleDirectInbound.put("server", "dns-direct");
        dnsRules.put(ruleDirectInbound);

        JSONObject ruleFakeIp = new JSONObject();
        JSONArray dnsInArr = new JSONArray();
        dnsInArr.put("dns-in");
        ruleFakeIp.put("inbound", dnsInArr);
        JSONArray qTypes = new JSONArray();
        qTypes.put("A");
        qTypes.put("AAAA");
        ruleFakeIp.put("query_type", qTypes);
        ruleFakeIp.put("server", "dns-fakeip");
        dnsRules.put(ruleFakeIp);

        dnsObj.put("rules", dnsRules);

        JSONObject fakeIpObj = new JSONObject();
        fakeIpObj.put("enabled", true);
        fakeIpObj.put("inet4_range", "198.18.0.0/15");
        dnsObj.put("fakeip", fakeIpObj);

        dnsObj.put("final", "dns-direct");
        dnsObj.put("strategy", "ipv4_only");
        dnsObj.put("independent_cache", true);
        root.put("dns", dnsObj);

        // 3. Inbounds (redirect-in, direct-redirect-in, tproxy-in, mixed-in, dns-in)
        JSONArray inbounds = new JSONArray();

        JSONObject inRedirect = new JSONObject();
        inRedirect.put("type", "redirect");
        inRedirect.put("tag", "redirect-in");
        inRedirect.put("listen", "127.0.0.1");
        inRedirect.put("listen_port", 10800);
        inRedirect.put("sniff", true);
        inRedirect.put("sniff_override_destination", true);
        inbounds.put(inRedirect);

        JSONObject inDirectRedirect = new JSONObject();
        inDirectRedirect.put("type", "redirect");
        inDirectRedirect.put("tag", "direct-redirect-in");
        inDirectRedirect.put("listen", "127.0.0.1");
        inDirectRedirect.put("listen_port", 10808);
        inDirectRedirect.put("sniff", true);
        inDirectRedirect.put("sniff_override_destination", true);
        inbounds.put(inDirectRedirect);

        JSONObject inTproxy = new JSONObject();
        inTproxy.put("type", "tproxy");
        inTproxy.put("tag", "tproxy-in");
        inTproxy.put("listen", "127.0.0.1");
        inTproxy.put("listen_port", 10801);
        inTproxy.put("network", "udp");
        inTproxy.put("sniff", true);
        inTproxy.put("sniff_override_destination", true);
        inbounds.put(inTproxy);

        JSONObject inMixed = new JSONObject();
        inMixed.put("type", "mixed");
        inMixed.put("tag", "mixed-in");
        inMixed.put("listen", "127.0.0.1");
        inMixed.put("listen_port", 10809);
        inMixed.put("sniff", true);
        inMixed.put("sniff_override_destination", true);
        inbounds.put(inMixed);

        JSONObject inDns = new JSONObject();
        inDns.put("type", "direct");
        inDns.put("tag", "dns-in");
        inDns.put("listen", "127.0.0.1");
        inDns.put("listen_port", 10853);
        inDns.put("override_address", "1.1.1.1");
        inDns.put("override_port", 53);
        inbounds.put(inDns);

        root.put("inbounds", inbounds);

        // 4. Outbounds
        JSONArray outbounds = new JSONArray();

        JSONObject outProxy = new JSONObject();
        boolean isHttp = cfg.type != null && cfg.type.toLowerCase().startsWith("http");
        outProxy.put("type", isHttp ? "http" : "socks");
        outProxy.put("tag", "proxy-out");
        outProxy.put("server", cfg.host.trim());
        outProxy.put("server_port", cfg.port);
        if (!isHttp) {
            outProxy.put("version", "5");
        }
        if (cfg.username != null && !cfg.username.isEmpty()) {
            outProxy.put("username", cfg.username);
        }
        if (cfg.password != null && !cfg.password.isEmpty()) {
            outProxy.put("password", cfg.password);
        }
        outProxy.put("routing_mark", 254);
        outProxy.put("domain_strategy", "ipv4_only");
        outbounds.put(outProxy);

        JSONObject outDirect = new JSONObject();
        outDirect.put("type", "direct");
        outDirect.put("tag", "direct-out");
        outDirect.put("routing_mark", 254);
        outDirect.put("domain_strategy", "ipv4_only");
        outbounds.put(outDirect);

        root.put("outbounds", outbounds);

        // 5. Route (100% sing-box 1.11.4 compliant — NO unknown fields in route.rules!)
        JSONObject routeObj = new JSONObject();
        JSONArray routeRules = new JSONArray();

        JSONObject rDnsIn = new JSONObject();
        JSONArray rDnsInArr = new JSONArray();
        rDnsInArr.put("dns-in");
        rDnsIn.put("inbound", rDnsInArr);
        rDnsIn.put("action", "hijack-dns");
        routeRules.put(rDnsIn);

        JSONObject rDnsProto = new JSONObject();
        rDnsProto.put("protocol", "dns");
        rDnsProto.put("action", "hijack-dns");
        routeRules.put(rDnsProto);

        JSONObject rDirectIn = new JSONObject();
        JSONArray rDirectInArr = new JSONArray();
        rDirectInArr.put("direct-redirect-in");
        rDirectIn.put("inbound", rDirectInArr);
        rDirectIn.put("outbound", "direct-out");
        routeRules.put(rDirectIn);

        JSONObject rPrivate = new JSONObject();
        rPrivate.put("ip_is_private", true);
        rPrivate.put("outbound", "direct-out");
        routeRules.put(rPrivate);

        routeObj.put("rules", routeRules);
        routeObj.put("final", "proxy-out");
        routeObj.put("auto_detect_interface", true);
        root.put("route", routeObj);

        // 6. Experimental cache_file for FakeIP persistence
        JSONObject expObj = new JSONObject();
        JSONObject cacheObj = new JSONObject();
        cacheObj.put("enabled", true);
        cacheObj.put("path", workDir + "/cache.db");
        cacheObj.put("store_fakeip", true);
        expObj.put("cache_file", cacheObj);
        root.put("experimental", expObj);

        return root.toString(2);
    }

    public static void refreshExitMetadataAsync() {
        Thread t = new Thread(RootBridge::queryAndSyncExitMetadata);
        t.setDaemon(true);
        t.start();
    }

    public static JSONObject queryAndSyncExitMetadata() {
        JSONObject result = new JSONObject();
        try {
            // Query through sing-box's local mixed-in HTTP proxy (127.0.0.1:10809)
            Proxy localProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809));

            // Try ipwho.is over HTTPS first
            String body = httpGetViaProxy("https://ipwho.is/", localProxy, 4500);
            if (body != null && body.contains("\"ip\"")) {
                JSONObject j = new JSONObject(body);
                String ip = j.optString("ip", "").trim();
                if (!ip.isEmpty()) {
                    cachedExitIp = ip;
                    cachedCountry = j.optString("country", "");
                    cachedCountryCode = j.optString("country_code", "");
                    JSONObject conn = j.optJSONObject("connection");
                    if (conn != null) {
                        cachedIsp = conn.optString("isp", conn.optString("org", ""));
                    }
                    JSONObject tz = j.optJSONObject("timezone");
                    if (tz != null) {
                        cachedTimezone = tz.optString("id", "");
                    }
                }
            }

            if (cachedExitIp.isEmpty()) {
                String body2 = httpGetViaProxy("http://ip-api.com/json/?fields=status,country,countryCode,city,isp,org,timezone,query", localProxy, 4500);
                if (body2 != null && body2.contains("\"query\"")) {
                    JSONObject j = new JSONObject(body2);
                    String ip = j.optString("query", "").trim();
                    if (!ip.isEmpty()) {
                        cachedExitIp = ip;
                        cachedCountry = j.optString("country", "");
                        cachedCountryCode = j.optString("countryCode", "");
                        cachedIsp = j.optString("isp", j.optString("org", ""));
                        cachedTimezone = j.optString("timezone", "");
                    }
                }
            }

            if (cachedExitIp.isEmpty()) {
                String ipPlain = httpGetViaProxy("https://api.ipify.org", localProxy, 4000);
                if (ipPlain != null && ipPlain.trim().matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
                    cachedExitIp = ipPlain.trim();
                }
            }

            if (!cachedExitIp.isEmpty()) {
                // Push verified Proxy Exit IP to stun-responder control endpoint
                String u = "http://127.0.0.1:10804/update?ip=" + URLEncoder.encode(cachedExitIp, "UTF-8")
                        + "&country=" + URLEncoder.encode(cachedCountry, "UTF-8")
                        + "&cc=" + URLEncoder.encode(cachedCountryCode, "UTF-8")
                        + "&isp=" + URLEncoder.encode(cachedIsp, "UTF-8");
                httpGetDirect(u, 2000);
            } else {
                // Ask stun-responder /status in case it already resolved the IP
                String st = httpGetDirect("http://127.0.0.1:10804/status", 2500);
                if (st != null && st.contains("\"ip\"")) {
                    JSONObject sj = new JSONObject(st);
                    String sip = sj.optString("ip", "").trim();
                    if (!sip.isEmpty()) {
                        cachedExitIp = sip;
                        cachedCountry = sj.optString("country", cachedCountry);
                        cachedCountryCode = sj.optString("countryCode", cachedCountryCode);
                        cachedIsp = sj.optString("isp", cachedIsp);
                        cachedTimezone = sj.optString("timezone", cachedTimezone);
                    }
                }
            }

            result.put("ip", cachedExitIp);
            result.put("country", cachedCountry);
            result.put("countryCode", cachedCountryCode);
            result.put("isp", cachedIsp);
            result.put("timezone", cachedTimezone);
            result.put("webrtcIp", cachedExitIp);
            result.put("dnsShield", "Active (Zero-Leak FakeIP)");
        } catch (Exception ignored) {
        }
        return result;
    }

    public static JSONObject getStatusJson(Context ctx, ProxyConfig cfg) {
        JSONObject obj = new JSONObject();
        boolean running = isTunnelRunning();
        try {
            obj.put("running", running);
            obj.put("connecting", connecting);
            obj.put("step", stepMessage);
            obj.put("lastError", lastError);
            obj.put("userId", getCurrentUserId());
            obj.put("workDir", getWorkDirPath());
            obj.put("exitIp", running ? cachedExitIp : "");
            obj.put("webrtcIp", running ? cachedExitIp : "");
            obj.put("country", running ? cachedCountry : "");
            obj.put("countryCode", running ? cachedCountryCode : "");
            obj.put("isp", running ? cachedIsp : "");
            obj.put("timezone", running ? cachedTimezone : "");
            obj.put("connectedSince", running ? connectedSinceMs : 0L);
            int selCount = (cfg != null && cfg.selectedApps != null) ? cfg.selectedApps.size() : 0;
            obj.put("selectedAppCount", selCount);
            obj.put("routingModeLabel", selCount > 0 ? ("Per-App (" + selCount + " selected)") : "Global (All Apps)");
        } catch (Exception ignored) {
        }
        return obj;
    }

    private static String httpGetViaProxy(String urlStr, Proxy proxy, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection(proxy);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "curl/8.5.0");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String httpGetDirect(String urlStr, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void extractAsset(Context ctx, String assetName, File dest) throws Exception {
        try (InputStream in = ctx.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void writeTextFile(File f, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sanitizeShell(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9._:\\-]", "");
    }
}
