package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"

    fun getBinaryPath() = "$ADB_DIR/sing-box"

    fun getActiveSlotFile(user: Int) =
        "$ADB_DIR/running_slot_u$user"

    fun getConfigPath(user: Int, slot: Int) =
        "$ADB_DIR/config_u${user}_s${slot}.json"

    fun getPidFile(user: Int, slot: Int) =
        "$ADB_DIR/singbox_u${user}_s${slot}.pid"

    fun getWatchdogPidFile(user: Int) =
        "$ADB_DIR/watchdog_u${user}.pid"

    fun getLogFile(user: Int, slot: Int) =
        "$ADB_DIR/singbox_u${user}_s${slot}.log"

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
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
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val slotFile = getActiveSlotFile(user)
        val out = executeSuWithOutput(listOf(
            "if [ -f $slotFile ]; then cat $slotFile; elif [ $user -eq 0 ] && [ -f $ADB_DIR/running_slot ]; then cat $ADB_DIR/running_slot; fi"
        )).lineSequence().lastOrNull()?.trim() ?: ""
        val s = out.toIntOrNull()
        return if (s != null && isRunning(user, s)) s else null
    }

    fun extractBinaryDirectly(context: Context): Boolean {
        val targetPath = getBinaryPath()
        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $ADB_DIR && rm -f $targetPath && cat > $targetPath && chmod 755 $targetPath && chcon u:object_r:system_file:s0 $targetPath 2>/dev/null"
            ).redirectErrorStream(true).start()
            context.assets.open("sing-box").use { input ->
                process.outputStream.use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun writeConfigDirectly(configContent: String, configPath: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $ADB_DIR && rm -f $configPath && cat > $configPath && chmod 644 $configPath"
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
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getActivePid(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String? {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then cat $pidFile; fi"
        ))
        return result.lineSequence().lastOrNull()?.trim()?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
    }

    fun clearLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot) {
        val logFile = getLogFile(user, slot)
        executeSu(listOf(": > $logFile"))
    }

    fun getDiagnosticsAndLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val pidFile = getPidFile(user, slot)
        val wdFile = getWatchdogPidFile(user)
        val logFile = getLogFile(user, slot)
        val raw = executeSuWithOutput(listOf(
            "echo '=== DAEMON STATUS (USER $user • SLOT P$slot) ==='",
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then",
            "  echo 'Core State: ACTIVE (PID: '\$(cat $pidFile)')'",
            "else",
            "  echo 'Core State: STOPPED / NOT RUNNING'",
            "fi",
            "if [ -f $wdFile ] && kill -0 \$(cat $wdFile) 2>/dev/null; then",
            "  echo 'Auto-Heal Watchdog: ARMED (PID: '\$(cat $wdFile)')'",
            "else",
            "  echo 'Auto-Heal Watchdog: INACTIVE'",
            "fi",
            "echo ''",
            "echo '=== RECENT LOG ENTRIES ==='",
            "if [ -f $logFile ]; then tail -n 120 $logFile; else echo 'No log file found.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    // Live Per-App Connection Sniffer (Reads active kernel sockets via ss -tupn)
    fun getLiveConnections(user: Int = ProfileManager.androidUserId): String {
        val raw = executeSuWithOutput(listOf(
            "echo '=== ACTIVE KERNEL SOCKETS (ss -tupn) ==='",
            "ss -tupn state established 2>/dev/null | head -n 90 || netstat -tupn 2>/dev/null | head -n 90"
        ))
        return raw.ifEmpty { "No active sockets found." }
    }

    fun startProxy(
        context: Context,
        settings: ProxySettings,
        selectedUids: List<Int>? = null,
        relaySettings: ProxySettings? = null
    ): StartResult {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val binaryPath = getBinaryPath()
        val configPath = getConfigPath(user, slot)
        val pidFile = getPidFile(user, slot)
        val wdFile = getWatchdogPidFile(user)
        val logFile = getLogFile(user, slot)
        val slotFile = getActiveSlotFile(user)
        val port = ProfileManager.localInboundPort

        val testRun = executeSuWithOutput(listOf("$binaryPath version 2>&1"))
        if (!testRun.contains("sing-box version")) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted) {
                return StartResult(success = false, errorMessage = "Failed to extract sing-box binary to $ADB_DIR")
            }
        }

        val configContent = ConfigGenerator.generateJson(settings, port, relaySettings)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        stopProxy(context, user)

        val runCmd = "$binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile && echo $slot > $slotFile"
        executeSu(listOf(runCmd))

        var alive = false
        for (attempt in 1..6) {
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

        val relayHost = relaySettings?.host?.trim() ?: ""
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids, relayHost)
        val ipSuccess = executeSu(iptablesCmds)

        if (!ipSuccess) {
            stopProxy(context, user)
            return StartResult(success = false, errorMessage = "Failed to apply kernel iptables rules")
        }

        // Launch Auto-Heal Kernel Watchdog Daemon if enabled
        if (settings.enableWatchdog) {
            val watchdogCmd = "(while [ -f $slotFile ]; do sleep 3; if [ -f $pidFile ] && ! kill -0 \$(cat $pidFile) 2>/dev/null; then $binaryPath run -c $configPath >> $logFile 2>&1 < /dev/null & echo \$! > $pidFile; fi; done) >/dev/null 2>&1 < /dev/null & echo \$! > $wdFile"
            executeSu(listOf(watchdogCmd))
        }

        return StartResult(success = true)
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId
    ): Boolean {
        val commands = mutableListOf<String>()
        val wdFile = getWatchdogPidFile(user)

        // 1. Disarm Auto-Heal Watchdog first so it doesn't respawn sing-box during stop
        commands.add("rm -f ${getActiveSlotFile(user)}")
        if (user == 0) {
            commands.add("rm -f $ADB_DIR/running_slot")
        }
        commands.add("if [ -f $wdFile ]; then kill -9 \$(cat $wdFile) 2>/dev/null; rm -f $wdFile; fi")

        // 2. Flush iptables rules and terminate this user profile's sing-box daemon
        for (s in 0 until ProfileManager.MAX_SLOTS) {
            commands.addAll(IptablesManager.generateDisableCommands(user, s))
            val pFile = getPidFile(user, s)
            commands.add("if [ -f $pFile ]; then kill -9 \$(cat $pFile) 2>/dev/null; rm -f $pFile; fi")
        }

        commands.add("pkill -9 -f 'config_u${user}_' 2>/dev/null")

        return executeSu(commands)
    }
}
