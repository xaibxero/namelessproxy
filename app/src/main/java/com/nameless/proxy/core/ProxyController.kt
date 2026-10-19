package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.net.InetAddress

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"

    fun getBinaryPath(): String = ADB_DIR + "/sing-box"

    fun getActiveSlotFile(user: Int): String =
        ADB_DIR + "/running_slot_u" + user

    fun getConfigPath(user: Int, slot: Int): String =
        ADB_DIR + "/config_u" + user + "_s" + slot + ".json"

    fun getPidFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".pid"

    fun getLogFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".log"

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                }
                os.write("exit\n".toByteArray(Charsets.UTF_8))
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

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val slotFile = getActiveSlotFile(user)
        val legacyFile = ADB_DIR + "/running_slot"
        val cmd = if (user == 0) {
            "if [ -f " + slotFile + " ]; then cat " + slotFile + "; elif [ -f " + legacyFile + " ]; then cat " + legacyFile + "; fi"
        } else {
            "if [ -f " + slotFile + " ]; then cat " + slotFile + "; fi"
        }
        val out = executeSuWithOutput(listOf(cmd)).lineSequence().lastOrNull()?.trim() ?: ""
        val s = out.toIntOrNull()
        return if (s != null && isRunning(user, s)) s else null
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
        return executeSu(IptablesManager.generateHandoverRefreshCommands(user, slot))
    }

    fun getDiagnosticsAndLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val raw = executeSuWithOutput(listOf(
            "echo '=== KERNEL DAEMON (U#" + user + " • P" + slot + ") ==='",
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then",
            "  echo 'Status  : ONLINE (PID '$(cat " + pidFile + ")')'",
            "else",
            "  echo 'Status  : STANDBY'",
            "fi",
            "echo 'PrivDNS : '$(settings get global private_dns_mode 2>/dev/null)",
            "echo ''",
            "echo '=== SING-BOX STREAM ==='",
            "if [ -f " + logFile + " ]; then tail -n 120 " + logFile + "; else echo 'No log entries.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun getKernelAuditReport(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val chainNatV4 = "NAMELESS_U" + user + "_S" + slot
        val chainNatV6 = "NAMELESS_U" + user + "_S" + slot + "_V6"
        val chainFilter = "NAMELESS_FILTER_u" + user + "_s" + slot
        val chainV6Filter = "NAMELESS_V6_FILTER_u" + user + "_s" + slot
        val raw = executeSuWithOutput(listOf(
            "echo '=== IPv6 464XLAT SAFE RULES ==='",
            "ip -6 rule show | grep -E '890|22000' || true",
            "echo ''",
            "echo '=== NAT DNS & WEBRTC ($chainNatV4) ==='",
            "iptables -w -t nat -S $chainNatV4 2>&1 || true",
            "echo ''",
            "echo '=== IPv6 NAT ($chainNatV6) ==='",
            "ip6tables -w -t nat -S $chainNatV6 2>&1 || true",
            "echo ''",
            "echo '=== KILL-SWITCH ($chainFilter) ==='",
            "iptables -w -S $chainFilter 2>&1 || true",
            "echo ''",
            "echo '=== IPv6 SHIELD ($chainV6Filter) ==='",
            "ip6tables -w -S $chainV6Filter 2>&1 || true"
        ))
        return raw.ifEmpty { "Unable to query netfilter." }
    }

    fun startProxy(
        context: Context,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): StartResult {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val binaryPath = getBinaryPath()
        val configPath = getConfigPath(user, slot)
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val slotFile = getActiveSlotFile(user)
        val port = ProfileManager.localInboundPort

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

        // Preserve original Private DNS mode BEFORE stopping any existing tunnels
        val curDnsMode = executeSuWithOutput(listOf("settings get global private_dns_mode")).trim()
        val modeToSave = if (curDnsMode.isEmpty() || curDnsMode == "null") "opportunistic" else curDnsMode
        if (modeToSave != "off") {
            executeSu(listOf("mkdir -p $ADB_DIR && echo '$modeToSave' > $ADB_DIR/saved_private_dns_mode"))
        }

        // Stop any running tunnels across ALL user profiles first so no other profile's iptables chains conflict!
        stopProxy(context, user, restorePrivateDns = false, cleanAllUsers = true)

        // Pre-resolve proxy server hostname (if user entered a domain instead of an IP) while network is clean
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

        executeSu(listOf(
            "settings put global private_dns_mode off",
            "cmd connectivity flush-dns 2>/dev/null || true",
            "ndc resolver flushdefaultif 2>/dev/null || true"
        ))

        val runCmd = "mkdir -p $ADB_DIR && rm -f $ADB_DIR/cache_u${user}_s${slot}.db && ( trap '' HUP; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile ) && echo $slot > $slotFile"
        executeSu(listOf(runCmd))

        var alive = false
        for (attempt in 1..8) {
            Thread.sleep(150)
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

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids, resolvedHost)
        val ipSuccess = executeSu(iptablesCmds)

        return if (ipSuccess) {
            StartResult(success = true)
        } else {
            stopProxy(context, user, restorePrivateDns = true, cleanAllUsers = false)
            StartResult(success = false, errorMessage = "Failed to apply kernel iptables rules")
        }
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId,
        restorePrivateDns: Boolean = true,
        cleanAllUsers: Boolean = true
    ): Boolean {
        val commands = mutableListOf<String>()

        // Discover all users that may have active NAMELESS chains (e.g. User 0, 10, 11, 12, 999)
        val usersToClean = if (cleanAllUsers) {
            setOf(0, 10, 11, 12, 13, 95, 999, user)
        } else {
            setOf(user)
        }

        for (u in usersToClean) {
            for (s in 0 until ProfileManager.MAX_SLOTS) {
                commands.addAll(IptablesManager.generateDisableCommands(u, s))
                val pFile = getPidFile(u, s)
                commands.add("if [ -f " + pFile + " ]; then kill -9 $(cat " + pFile + ") 2>/dev/null; rm -f " + pFile + "; fi")
            }
            commands.add("rm -f " + getActiveSlotFile(u))
        }

        commands.add("rm -f " + ADB_DIR + "/running_slot")
        if (cleanAllUsers) {
            commands.add("killall -9 sing-box 2>/dev/null || true")
            commands.add("pkill -9 -f 'sing-box' 2>/dev/null || true")
        } else {
            commands.add("pkill -9 -f 'config_u" + user + "_' 2>/dev/null || true")
        }

        if (restorePrivateDns) {
            commands.add(
                "if [ -f $ADB_DIR/saved_private_dns_mode ]; then " +
                    "settings put global private_dns_mode \$(cat $ADB_DIR/saved_private_dns_mode) 2>/dev/null; " +
                    "rm -f $ADB_DIR/saved_private_dns_mode; " +
                "fi"
            )
            commands.add("cmd connectivity flush-dns 2>/dev/null || true")
            commands.add("ndc resolver flushdefaultif 2>/dev/null || true")
        }

        return executeSu(commands)
    }
}
