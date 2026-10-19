package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream

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
                os.write("exit\n".toByteArray(Charsets.UTF_8))
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
            "echo '=== KERNEL DAEMON STATUS (USER " + user + " • SLOT P" + slot + ") ==='",
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then",
            "  echo 'State   : ACTIVE (PID '$(cat " + pidFile + ")')'",
            "else",
            "  echo 'State   : STOPPED'",
            "fi",
            "echo 'PrivDNS : '$(settings get global private_dns_mode 2>/dev/null)",
            "echo 'ActiveIF: '$(ip route show table all 2>/dev/null | grep -E 'default via|dev (wlan|rmnet|v4-rmnet|ccmni)' | head -n 3 | tr '\\n' ' ')",
            "echo ''",
            "echo '=== RECENT SING-BOX LOG STREAM ==='",
            "if [ -f " + logFile + " ]; then tail -n 120 " + logFile + "; else echo 'No log file found.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun getKernelAuditReport(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val chainNatV4 = "NAMELESS_U" + user + "_S" + slot
        val chainNatV6 = "NAMELESS_U" + user + "_S" + slot + "_V6"
        val chainFilter = "NAMELESS_FILTER_u" + user + "_s" + slot
        val chainV6Filter = "NAMELESS_V6_FILTER_u" + user + "_s" + slot
        val raw = executeSuWithOutput(listOf(
            "echo '=== IPv4 POLICY RULES ==='",
            "ip rule show | grep -E '1800|1850|22000' || true",
            "echo ''",
            "echo '=== IPv6 POLICY RULES (464XLAT SAFE) ==='",
            "ip -6 rule show | grep -E '890|1800|22000' || true",
            "echo ''",
            "echo '=== NAT DNS & WEBRTC SHIELD ($chainNatV4) ==='",
            "iptables -w -t nat -S $chainNatV4 2>&1 || true",
            "echo ''",
            "echo '=== IPv6 NAT DNS SHIELD ($chainNatV6) ==='",
            "ip6tables -w -t nat -S $chainNatV6 2>&1 || true",
            "echo ''",
            "echo '=== OUTPUT KILL-SWITCH ($chainFilter) ==='",
            "iptables -w -S $chainFilter 2>&1 || true",
            "echo ''",
            "echo '=== IPv6 OUTPUT SHIELD ($chainV6Filter) ==='",
            "ip6tables -w -S $chainV6Filter 2>&1 || true"
        ))
        return raw.ifEmpty { "Unable to query kernel netfilter tables." }
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
                    errorMessage = "sing-box binary invalid or missing at $binaryPath:\n$diag"
                )
            }
        }

        val configContent = ConfigGenerator.generateJson(settings, port)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        // Preserve original Private DNS mode BEFORE stopProxy or overwriting
        // Note: On factory-default Android, 'settings get global private_dns_mode' returns "null"
        // which Android treats as "opportunistic" (DoT on port 853). We MUST always force it to 'off'!
        val curDnsMode = executeSuWithOutput(listOf("settings get global private_dns_mode")).trim()
        val modeToSave = if (curDnsMode.isEmpty() || curDnsMode == "null") "opportunistic" else curDnsMode
        if (modeToSave != "off") {
            executeSu(listOf("mkdir -p $ADB_DIR && echo '$modeToSave' > $ADB_DIR/saved_private_dns_mode"))
        }

        stopProxy(context, user, restorePrivateDns = false)

        // Always force Private DNS off while tunnel is active so Android netd uses port 53 (hijacked to sing-box)
        executeSu(listOf(
            "settings put global private_dns_mode off",
            "cmd connectivity flush-dns 2>/dev/null || true",
            "ndc resolver flushdefaultif 2>/dev/null || true"
        ))

        val runCmd = "mkdir -p $ADB_DIR && ( trap '' HUP; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile ) && echo $slot > $slotFile"
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

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)
        val ipSuccess = executeSu(iptablesCmds)

        return if (ipSuccess) {
            StartResult(success = true)
        } else {
            stopProxy(context, user, restorePrivateDns = true)
            StartResult(success = false, errorMessage = "Failed to apply kernel iptables rules")
        }
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId,
        restorePrivateDns: Boolean = true
    ): Boolean {
        val commands = mutableListOf<String>()

        for (s in 0 until ProfileManager.MAX_SLOTS) {
            commands.addAll(IptablesManager.generateDisableCommands(user, s))
            val pFile = getPidFile(user, s)
            commands.add("if [ -f " + pFile + " ]; then kill -9 $(cat " + pFile + ") 2>/dev/null; rm -f " + pFile + "; fi")
        }

        commands.add("rm -f " + getActiveSlotFile(user))
        if (user == 0) {
            commands.add("rm -f " + ADB_DIR + "/running_slot")
        }
        commands.add("pkill -9 -f 'config_u" + user + "_' 2>/dev/null || true")

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
