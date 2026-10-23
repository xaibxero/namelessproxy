package com.nameless.proxy.core

import android.content.Context
import com.nameless.proxy.service.ProxyKeepAliveService
import java.io.DataOutputStream
import java.net.InetAddress

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"
    private const val DAEMON_SCRIPT = "$ADB_DIR/nameless_daemon.sh"
    private const val DAEMON_PID_FILE = "$ADB_DIR/daemon.pid"
    private const val STUN_PID_FILE = "$ADB_DIR/stun_daemon.pid"
    private const val EXIT_IP_FILE = "$ADB_DIR/exit_ip"

    @Volatile
    private var lastActiveSettings: ProxySettings = ProxySettings()

    fun getBinaryPath(): String = ADB_DIR + "/sing-box"

    fun getActiveSlotFile(user: Int): String =
        ADB_DIR + "/running_slot_u" + user

    fun getConfigPath(user: Int, slot: Int): String =
        ADB_DIR + "/config_u" + user + "_s" + slot + ".json"

    fun getPidFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".pid"

    fun getLogFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".log"

    fun getLastActiveSettings(context: Context? = null, slot: Int = ProfileManager.activeSlot): ProxySettings {
        if (lastActiveSettings.host.isNotBlank()) return lastActiveSettings
        if (context != null) {
            try {
                val sp = context.getSharedPreferences("nameless_slot_$slot", Context.MODE_PRIVATE)
                val globalPrefs = context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
                val h = sp.getString("host", "")?.trim() ?: ""
                if (h.isNotEmpty()) {
                    lastActiveSettings = ProxySettings(
                        type = runCatching { ProxyType.valueOf(sp.getString("proxy_type", "SOCKS5")!!) }.getOrDefault(ProxyType.SOCKS5),
                        transportMode = runCatching { TransportMode.valueOf(sp.getString("transport_mode", "TCP_AND_UDP")!!) }.getOrDefault(TransportMode.TCP_AND_UDP),
                        ipMode = runCatching { IpMode.valueOf(sp.getString("ip_mode", "IPV4_ONLY")!!) }.getOrDefault(IpMode.IPV4_ONLY),
                        host = h,
                        port = sp.getString("port", "1080")?.toIntOrNull() ?: 1080,
                        username = sp.getString("username", "") ?: "",
                        password = sp.getString("password", "") ?: "",
                        routeHotspot = globalPrefs.getBoolean("route_hotspot", true),
                        sni = sp.getString("sni", "") ?: "",
                        ssMethod = sp.getString("ss_method", "chacha20-ietf-poly1305") ?: "chacha20-ietf-poly1305",
                        realityPublicKey = sp.getString("reality_pk", "") ?: "",
                        realityShortId = sp.getString("reality_sid", "") ?: "",
                        strictWebRtcShield = true
                    )
                }
            } catch (_: Exception) {
            }
        }
        return lastActiveSettings
    }

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                }
                os.write("exit 0\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    private fun executeSu(commands: List<String>): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                }
                os.write("exit 0\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isBinaryExecutable(): Boolean {
        val targetPath = getBinaryPath()
        val check = executeSuWithOutput(listOf(
            "if [ -x $targetPath ]; then $targetPath version 2>&1; else echo 'MISSING'; fi"
        ))
        return check.contains("sing-box version") && !check.contains("No such file") && !check.contains("cannot execute")
    }

    fun ensureCurrentProfileExclusive(context: Context) {
        val currentUser = ProfileManager.androidUserId
        val rawUser = executeSuWithOutput(listOf(
            "if [ -f $ADB_DIR/running_user ]; then cat $ADB_DIR/running_user; fi"
        )).lineSequence().lastOrNull()?.trim() ?: ""
        val runningUser = rawUser.toIntOrNull()
        if (runningUser != null && runningUser != currentUser) {
            stopProxy(context, runningUser)
        }
    }

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val slotFile = getActiveSlotFile(user)
        val cmd = "if [ -f $slotFile ]; then cat $slotFile; elif [ -f $ADB_DIR/running_slot ]; then cat $ADB_DIR/running_slot; fi"
        val out = executeSuWithOutput(listOf(cmd)).lineSequence().lastOrNull()?.trim() ?: ""
        val s = out.toIntOrNull()
        val running = if (s != null && isRunning(user, s)) s else null
        if (running != null) {
            val port = 10800 + (user * 100) + (running * 10)
            val cfg = lastActiveSettings
            val cleanHost = ConfigGenerator.cleanHost(cfg.host)
            StunResponder.ensureRunning(
                stunPort = port + 3,
                socksPort = port + 1,
                fallbackHostIp = cleanHost.takeIf { IptablesManager.isValidIpv4(it) },
                enableLocalStun = true,
                enableHotspotStun = cfg.routeHotspot
            )
        }
        return running
    }

    fun extractBinaryDirectly(context: Context): Boolean {
        val targetPath = getBinaryPath()
        return try {
            val assetStream = context.assets.open("sing-box")

            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $ADB_DIR && cat > $targetPath && chmod 755 $targetPath && chcon u:object_r:system_file:s0 $targetPath 2>/dev/null || true"
            ).redirectErrorStream(true).start()

            assetStream.use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (process.waitFor() != 0) return false

            isBinaryExecutable()
        } catch (e: Exception) {
            false
        }
    }

    fun writeConfigDirectly(configContent: String, configPath: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p " + ADB_DIR + " && rm -f " + configPath + " && cat > " + configPath + " && chmod 644 " + configPath
            ).redirectErrorStream(true).start()
            process.outputStream.use { os ->
                os.write(configContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isRunning(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): Boolean {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then echo 'RUNNING'; " +
            "elif pidof sing-box >/dev/null 2>&1; then pidof sing-box | awk '{print \$1}' > $pidFile; echo 'RUNNING'; " +
            "else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getActivePid(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String? {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then cat $pidFile; " +
            "elif pidof sing-box >/dev/null 2>&1; then pidof sing-box | awk '{print \$1}'; fi"
        ))
        return result.lineSequence().lastOrNull()?.trim()?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
    }

    fun clearLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot) {
        val logFile = getLogFile(user, slot)
        executeSu(listOf(": > " + logFile))
    }

    fun refreshHandoverRules(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): Boolean {
        if (!isRunning(user, slot)) return false
        val port = 10800 + (user * 100) + (slot * 10)
        val cfg = lastActiveSettings
        val cleanHost = ConfigGenerator.cleanHost(cfg.host)
        StunResponder.ensureRunning(
            stunPort = port + 3,
            socksPort = port + 1,
            fallbackHostIp = cleanHost.takeIf { IptablesManager.isValidIpv4(it) },
            enableLocalStun = true,
            enableHotspotStun = cfg.routeHotspot
        )
        return executeSu(IptablesManager.generateHandoverRefreshCommands(cfg.routeHotspot))
    }

    fun applyLiveSettingsUpdate(
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): Boolean {
        lastActiveSettings = settings
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        if (!isRunning(user, slot)) return false
        val port = ProfileManager.localInboundPort

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val resolvedHost = if (!IptablesManager.isValidIpv4(rawHost) && !IptablesManager.isValidIpv6(rawHost)) {
            try {
                InetAddress.getByName(rawHost).hostAddress ?: rawHost
            } catch (_: Exception) {
                rawHost
            }
        } else {
            rawHost
        }

        StunResponder.start(
            stunPort = port + 3,
            socksPort = port + 1,
            fallbackHostIp = resolvedHost.takeIf { IptablesManager.isValidIpv4(it) },
            enableLocalStun = true,
            enableHotspotStun = settings.routeHotspot
        )

        val iptablesCmds = IptablesManager.generateEnableCommands(
            inboundPort = port,
            settings = settings,
            selectedUids = selectedUids,
            resolvedServerHost = resolvedHost,
            skipInitialClean = false
        )
        return executeSu(iptablesCmds)
    }

    fun getDiagnosticsAndLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val stunIp: String = StunResponder.getExitIp().ifEmpty { "Standby" }
        val raw = executeSuWithOutput(listOf(
            "echo '=== KERNEL DAEMON (U#" + user + " • SLOT P" + (slot + 1) + ") ==='",
            "if [ -f " + pidFile + " ] && kill -0 \$(cat " + pidFile + ") 2>/dev/null; then",
            "  echo 'Status   : ONLINE (PID '\$(cat " + pidFile + ")')'",
            "elif pidof sing-box >/dev/null 2>&1; then",
            "  echo 'Status   : ONLINE (PID '\$(pidof sing-box)')'",
            "else",
            "  echo 'Status   : STANDBY'",
            "fi",
            "if [ -f $DAEMON_PID_FILE ] && kill -0 \$(cat $DAEMON_PID_FILE) 2>/dev/null; then",
            "  echo 'Watchdog : ACTIVE (PID '\$(cat $DAEMON_PID_FILE)')'",
            "fi",
            "echo 'STUN IP  : '\$(if [ -f $EXIT_IP_FILE ]; then cat $EXIT_IP_FILE; else echo '$stunIp'; fi)",
            "echo 'PrivDNS  : '\$(settings get global private_dns_mode 2>/dev/null)",
            "echo ''",
            "echo '=== SING-BOX STREAM ==='",
            "if [ -f " + logFile + " ]; then tail -n 120 " + logFile + "; else echo 'No log entries.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun getKernelAuditReport(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val raw = executeSuWithOutput(listOf(
            "echo '=== ROUTING RULES ==='",
            "ip rule show | grep -E '8500|8501|8502|8503|22000|31000' || true",
            "echo ''",
            "echo '=== NAT DNS & WEBRTC (${IptablesManager.CHAIN_NAT_V4}) ==='",
            "iptables -w -t nat -S ${IptablesManager.CHAIN_NAT_V4} 2>&1 || true",
            "echo ''",
            "echo '=== HOTSPOT NAT & STUN (${IptablesManager.CHAIN_HS_NAT}) ==='",
            "iptables -w -t nat -S ${IptablesManager.CHAIN_HS_NAT} 2>&1 || true",
            "echo ''",
            "echo '=== FILTER (${IptablesManager.CHAIN_FILTER_V4}) ==='",
            "iptables -w -S ${IptablesManager.CHAIN_FILTER_V4} 2>&1 || true",
            "echo ''",
            "echo '=== IPv6 FILTER (${IptablesManager.CHAIN_FILTER_V6}) ==='",
            "ip6tables -w -S ${IptablesManager.CHAIN_FILTER_V6} 2>&1 || true"
        ))
        return raw.ifEmpty { "Unable to query netfilter." }
    }

    /**
     * Builds the unkillable `/data/adb/nameless_proxy/nameless_daemon.sh` watchdog script.
     * Runs in `/data/adb/nameless_proxy` as root (`UID 0`) with `oom_score_adj = -1000` and
     * keeps `sing-box`, `RootStunDaemon`, `iptables`, and `ip rule` alive 24/7 even if the app is closed.
     */
    private fun buildLiveDaemonScript(
        apkPath: String,
        binaryPath: String,
        configPath: String,
        pidFile: String,
        logFile: String,
        slotFile: String,
        stunPort: Int,
        socksPort: Int,
        fallbackIp: String,
        routeHotspot: Boolean
    ): String {
        val hsFlag = if (routeHotspot) "true" else "false"
        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# Nameless Proxy Unkillable Root Daemon (/data/adb/nameless_proxy/nameless_daemon.sh)\n")
        sb.append("export PATH=/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH:/system/bin:/system/xbin\n")
        sb.append("trap '' HUP INT TERM\n")
        sb.append("echo \$\$ > $DAEMON_PID_FILE\n")
        sb.append("echo -1000 > /proc/\$\$/oom_score_adj 2>/dev/null || true\n")
        sb.append("echo \$\$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true\n\n")

        sb.append("while [ -f $slotFile ]; do\n")
        sb.append("  # 1. Ensure sing-box daemon is alive and protected from OOM/Freezer\n")
        sb.append("  if [ ! -f $pidFile ] || ! kill -0 \$(cat $pidFile 2>/dev/null) 2>/dev/null; then\n")
        sb.append("    if pidof sing-box >/dev/null 2>&1; then\n")
        sb.append("      pidof sing-box | awk '{print \$1}' > $pidFile\n")
        sb.append("    else\n")
        sb.append("      ( trap '' HUP INT TERM; $binaryPath run -c $configPath >> $logFile 2>&1 < /dev/null & echo \$! > $pidFile )\n")
        sb.append("      sleep 1\n")
        sb.append("    fi\n")
        sb.append("  fi\n")
        sb.append("  SB_PID=\$(cat $pidFile 2>/dev/null)\n")
        sb.append("  if [ -n \"\$SB_PID\" ]; then\n")
        sb.append("    echo -1000 > /proc/\$SB_PID/oom_score_adj 2>/dev/null || true\n")
        sb.append("    echo \$SB_PID > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true\n")
        sb.append("  fi\n\n")

        sb.append("  # 2. Ensure standalone RootStunDaemon (via /system/bin/app_process) is alive in /data/adb/nameless_proxy\n")
        sb.append("  if [ ! -f $STUN_PID_FILE ] || ! kill -0 \$(cat $STUN_PID_FILE 2>/dev/null) 2>/dev/null; then\n")
        sb.append("    if [ -f \"$apkPath\" ]; then\n")
        sb.append("      ( trap '' HUP INT TERM; CLASSPATH=\"$apkPath\" /system/bin/app_process $ADB_DIR com.nameless.proxy.core.RootStunDaemon \"$stunPort\" \"$socksPort\" \"$fallbackIp\" \"$hsFlag\" >/dev/null 2>&1 < /dev/null & echo \$! > $STUN_PID_FILE )\n")
        sb.append("    fi\n")
        sb.append("  fi\n")
        sb.append("  ST_PID=\$(cat $STUN_PID_FILE 2>/dev/null)\n")
        sb.append("  if [ -n \"\$ST_PID\" ]; then\n")
        sb.append("    echo -1000 > /proc/\$ST_PID/oom_score_adj 2>/dev/null || true\n")
        sb.append("    echo \$ST_PID > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true\n")
        sb.append("  fi\n\n")

        sb.append("  # 3. Ensure kernel routing & netfilter hooks stay at position #1 even if Android netd refreshes\n")
        sb.append("  ip route replace default dev lo table 2088 2>/dev/null || true\n")
        sb.append("  if ! ip rule show | grep -q '31000:'; then\n")
        sb.append("    ip rule add iif lo lookup 2088 pref 31000 2>/dev/null || true\n")
        sb.append("  fi\n")
        sb.append("  iptables -w -C INPUT -j ${IptablesManager.CHAIN_HS_IN} 2>/dev/null || iptables -w -I INPUT 1 -j ${IptablesManager.CHAIN_HS_IN} 2>/dev/null || true\n")
        sb.append("  iptables -w -C OUTPUT -j ${IptablesManager.CHAIN_FILTER_V4} 2>/dev/null || iptables -w -I OUTPUT 1 -j ${IptablesManager.CHAIN_FILTER_V4} 2>/dev/null || true\n")
        sb.append("  iptables -w -t nat -C OUTPUT -j ${IptablesManager.CHAIN_NAT_V4} 2>/dev/null || iptables -w -t nat -I OUTPUT 1 -j ${IptablesManager.CHAIN_NAT_V4} 2>/dev/null || true\n")
        sb.append("  ip6tables -w -C OUTPUT -j ${IptablesManager.CHAIN_FILTER_V6} 2>/dev/null || ip6tables -w -I OUTPUT 1 -j ${IptablesManager.CHAIN_FILTER_V6} 2>/dev/null || true\n")
        if (routeHotspot) {
            sb.append("  iptables -w -t nat -C PREROUTING -j ${IptablesManager.CHAIN_HS_NAT} 2>/dev/null || iptables -w -t nat -I PREROUTING 1 -j ${IptablesManager.CHAIN_HS_NAT} 2>/dev/null || true\n")
            sb.append("  iptables -w -C FORWARD -j ${IptablesManager.CHAIN_HS_FWD} 2>/dev/null || iptables -w -I FORWARD 1 -j ${IptablesManager.CHAIN_HS_FWD} 2>/dev/null || true\n")
        }
        sb.append("  sleep 2\n")
        sb.append("done\n")
        return sb.toString()
    }

    fun startProxy(
        context: Context,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): StartResult {
        lastActiveSettings = settings
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val binaryPath = getBinaryPath()
        val configPath = getConfigPath(user, slot)
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val slotFile = getActiveSlotFile(user)
        val port = ProfileManager.localInboundPort
        val apkPath = context.applicationInfo.sourceDir

        if (!isBinaryExecutable()) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted || !isBinaryExecutable()) {
                val diag = executeSuWithOutput(listOf(
                    "ls -la $binaryPath 2>&1",
                    "$binaryPath version 2>&1"
                ))
                return StartResult(
                    success = false,
                    errorMessage = "sing-box binary missing at $binaryPath:\n$diag"
                )
            }
        }

        stopProxy(context, user)

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val resolvedHost = if (!IptablesManager.isValidIpv4(rawHost) && !IptablesManager.isValidIpv6(rawHost)) {
            try {
                InetAddress.getByName(rawHost).hostAddress ?: rawHost
            } catch (_: Exception) {
                rawHost
            }
        } else {
            rawHost
        }

        val configContent = ConfigGenerator.generateJson(settings, port, resolvedHost)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        val fallbackIp = resolvedHost.takeIf { IptablesManager.isValidIpv4(it) } ?: ""
        val daemonScriptContent = buildLiveDaemonScript(
            apkPath = apkPath,
            binaryPath = binaryPath,
            configPath = configPath,
            pidFile = pidFile,
            logFile = logFile,
            slotFile = slotFile,
            stunPort = port + 3,
            socksPort = port + 1,
            fallbackIp = fallbackIp,
            routeHotspot = settings.routeHotspot
        )
        writeConfigDirectly(daemonScriptContent, DAEMON_SCRIPT)

        val runCmd = listOf(
            "mkdir -p $ADB_DIR",
            "chmod 755 $DAEMON_SCRIPT 2>/dev/null || true",
            "rm -f $ADB_DIR/cache_u${user}_s${slot}.db",
            "if [ -n '$fallbackIp' ]; then echo '$fallbackIp' > $EXIT_IP_FILE && chmod 644 $EXIT_IP_FILE; fi",
            "echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true",
            "cmd deviceidle whitelist +com.nameless.proxy 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy WAKE_LOCK allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy START_FOREGROUND allow 2>/dev/null || true",
            "echo $slot > $slotFile",
            "echo $slot > $ADB_DIR/running_slot",
            "echo $user > $ADB_DIR/running_user",
            "( trap '' HUP INT TERM; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile )",
            "SB_PID=\$(cat $pidFile 2>/dev/null)",
            "if [ -n \"\$SB_PID\" ]; then",
            "  echo -1000 > /proc/\$SB_PID/oom_score_adj 2>/dev/null || true",
            "  echo \$SB_PID > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true",
            "fi",
            "( trap '' HUP INT TERM; CLASSPATH=\"$apkPath\" /system/bin/app_process $ADB_DIR com.nameless.proxy.core.RootStunDaemon \"${port + 3}\" \"${port + 1}\" \"$fallbackIp\" \"${if (settings.routeHotspot) "true" else "false"}\" >/dev/null 2>&1 < /dev/null & echo \$! > $STUN_PID_FILE )",
            "( trap '' HUP INT TERM; /system/bin/sh $DAEMON_SCRIPT >/dev/null 2>&1 < /dev/null & )"
        )
        executeSu(runCmd)

        var alive = false
        for (attempt in 1..6) {
            Thread.sleep(120)
            if (isRunning(user, slot)) {
                alive = true
                if (attempt >= 2) break
            }
        }

        if (!alive || !isRunning(user, slot)) {
            val failureInfo = getDiagnosticsAndLogs(user, slot)
            return StartResult(
                success = false,
                errorMessage = failureInfo.ifEmpty { "sing-box daemon failed to start" }
            )
        }

        ProxyKeepAliveService.start(context)

        StunResponder.start(
            stunPort = port + 3,
            socksPort = port + 1,
            fallbackHostIp = fallbackIp.ifEmpty { null },
            enableLocalStun = true,
            enableHotspotStun = settings.routeHotspot
        )

        val iptablesCmds = IptablesManager.generateEnableCommands(
            inboundPort = port,
            settings = settings,
            selectedUids = selectedUids,
            resolvedServerHost = resolvedHost,
            skipInitialClean = true
        )
        val ipSuccess = executeSu(iptablesCmds)

        return if (ipSuccess) {
            StartResult(success = true)
        } else {
            stopProxy(context, user)
            StartResult(success = false, errorMessage = "Failed to apply kernel iptables rules")
        }
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId
    ): Boolean {
        ProxyKeepAliveService.stop(context)
        StunResponder.stop()
        val commands = mutableListOf<String>()

        // Remove slot files first so nameless_daemon.sh exits its loop cleanly
        commands.add("rm -f $ADB_DIR/running_slot* $ADB_DIR/running_user 2>/dev/null || true")
        commands.add("if [ -f $DAEMON_PID_FILE ]; then kill -9 \$(cat $DAEMON_PID_FILE) 2>/dev/null || true; rm -f $DAEMON_PID_FILE; fi")
        commands.add("if [ -f $STUN_PID_FILE ]; then kill -9 \$(cat $STUN_PID_FILE) 2>/dev/null || true; rm -f $STUN_PID_FILE; fi")
        commands.add("pkill -9 -f 'nameless_daemon.sh' 2>/dev/null || true")
        commands.add("pkill -9 -f 'RootStunDaemon' 2>/dev/null || true")
        commands.add("killall -9 sing-box 2>/dev/null || true")
        commands.add("pkill -9 -f 'sing-box' 2>/dev/null || true")
        commands.add("rm -f $ADB_DIR/singbox_*.pid $EXIT_IP_FILE 2>/dev/null || true")

        commands.addAll(IptablesManager.generateDisableCommands(user, ProfileManager.activeSlot))

        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return executeSu(commands)
    }
}
