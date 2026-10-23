package com.nameless.proxy.core

import android.content.Context
import android.os.Process
import com.nameless.proxy.service.ProxyKeepAliveService
import java.io.DataOutputStream
import java.net.InetAddress

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"

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
            // Another user profile was previously running and was closed/switched; clean it up for this profile
            stopProxy(context, runningUser)
        }
    }

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val slotFile = getActiveSlotFile(user)
        val cmd = "if [ -f " + slotFile + " ]; then cat " + slotFile + "; fi"
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
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getActivePid(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String? {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then cat " + pidFile + "; fi"
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
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then",
            "  echo 'Status   : ONLINE (PID '$(cat " + pidFile + ")')'",
            "else",
            "  echo 'Status   : STANDBY'",
            "fi",
            "echo 'STUN IP  : $stunIp'",
            "echo 'PrivDNS  : '$(settings get global private_dns_mode 2>/dev/null)",
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
        val appPid = Process.myPid()

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

        // Cleanly stop any previous profile's daemon/rules so only the currently active profile runs
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

        // Launch sing-box detached from the app's cgroup and exempt both sing-box and com.nameless.proxy
        // from Android 12–15's Cached App Freezer, Doze, and OOM killer!
        val runCmd = listOf(
            "mkdir -p $ADB_DIR",
            "rm -f $ADB_DIR/cache_u${user}_s${slot}.db",
            "echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true",
            "cmd deviceidle whitelist +com.nameless.proxy 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy WAKE_LOCK allow 2>/dev/null || true",
            "cmd appops set --user $user com.nameless.proxy START_FOREGROUND allow 2>/dev/null || true",
            "( trap '' HUP INT TERM; setsid $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile ) 2>/dev/null || ( trap '' HUP; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile )",
            "SB_PID=\$(cat $pidFile 2>/dev/null)",
            "if [ -n \"\$SB_PID\" ]; then",
            "  echo -1000 > /proc/\$SB_PID/oom_score_adj 2>/dev/null || true",
            "  echo \$SB_PID > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true",
            "  echo \$SB_PID > /dev/cpuctl/tasks 2>/dev/null || true",
            "  echo \$SB_PID > /dev/cgroup/cpu/tasks 2>/dev/null || true",
            "fi",
            "echo -900 > /proc/$appPid/oom_score_adj 2>/dev/null || true",
            "echo $appPid > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true",
            "echo $slot > $slotFile",
            "echo $slot > $ADB_DIR/running_slot",
            "echo $user > $ADB_DIR/running_user"
        )
        executeSu(runCmd)

        var alive = false
        for (attempt in 1..6) {
            Thread.sleep(100)
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
            fallbackHostIp = resolvedHost.takeIf { IptablesManager.isValidIpv4(it) },
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

        commands.add("killall -9 sing-box 2>/dev/null || true")
        commands.add("pkill -9 -f 'sing-box' 2>/dev/null || true")
        commands.add("rm -f $ADB_DIR/singbox_*.pid $ADB_DIR/running_slot* $ADB_DIR/running_user 2>/dev/null || true")

        commands.addAll(IptablesManager.generateDisableCommands(user, ProfileManager.activeSlot))

        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return executeSu(commands)
    }
}
