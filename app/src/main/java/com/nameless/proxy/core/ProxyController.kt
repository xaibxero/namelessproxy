package com.nameless.proxy.core

import android.content.Context
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"
    private const val ACTIVE_SLOT_FILE = "$ADB_DIR/running_slot"

    fun getBinaryPath() = "$ADB_DIR/sing-box"

    fun getConfigPath(user: Int, slot: Int) =
        "$ADB_DIR/config_u${user}_s${slot}.json"

    fun getPidFile(user: Int, slot: Int) =
        "$ADB_DIR/singbox_u${user}_s${slot}.pid"

    fun getLogFile(user: Int, slot: Int) =
        "$ADB_DIR/singbox_u${user}_s${slot}.log"

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            process.waitFor()
            sb.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun executeSu(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val out = executeSuWithOutput(listOf(
            "if [ -f $ACTIVE_SLOT_FILE ]; then cat $ACTIVE_SLOT_FILE; fi"
        )).trim()
        val s = out.toIntOrNull()
        return if (s != null && isRunning(user, s)) s else null
    }

    fun extractBinaryDirectly(context: Context): Boolean {
        val targetPath = getBinaryPath()
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $ADB_DIR && rm -f $targetPath && cat > $targetPath && chmod 755 $targetPath && chcon u:object_r:system_file:s0 $targetPath 2>/dev/null"))
            context.assets.open("sing-box").use { input ->
                input.copyTo(process.outputStream)
            }
            process.outputStream.flush()
            process.outputStream.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun writeConfigDirectly(configContent: String, configPath: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $ADB_DIR && rm -f $configPath && cat > $configPath && chmod 644 $configPath"))
            process.outputStream.write(configContent.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            process.outputStream.close()
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
        return result.trim().ifEmpty { null }
    }

    fun clearLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot) {
        val logFile = getLogFile(user, slot)
        executeSu(listOf("> $logFile"))
    }

    fun getDiagnosticsAndLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val raw = executeSuWithOutput(listOf(
            "echo '=== DAEMON STATUS (USER $user • SLOT $slot) ==='",
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then",
            "  echo 'State: ACTIVE (PID: '\$(cat $pidFile)')'",
            "else",
            "  echo 'State: STOPPED / NOT RUNNING'",
            "fi",
            "echo ''",
            "echo '=== RECENT LOG ENTRIES ==='",
            "if [ -f $logFile ]; then tail -n 120 $logFile; else echo 'No log file found.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
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
        val port = ProfileManager.localInboundPort

        // Verify binary presence
        val testRun = executeSuWithOutput(listOf("$binaryPath version 2>&1"))
        if (!testRun.contains("sing-box version")) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted) {
                return StartResult(success = false, errorMessage = "Failed to extract core binary to $ADB_DIR")
            }
        }

        // Generate and write slot configuration
        val configContent = ConfigGenerator.generateJson(settings, port)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        // Stop any running instance cleanly
        stopProxy(context, user)

        // Launch directly without nohup
        val runCmd = "$binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile && echo $slot > $ACTIVE_SLOT_FILE"
        executeSu(listOf(runCmd))

        Thread.sleep(700)
        if (!isRunning(user, slot)) {
            val failureInfo = getDiagnosticsAndLogs(user, slot)
            return StartResult(
                success = false,
                errorMessage = failureInfo.ifEmpty { "sing-box daemon failed to start" }
            )
        }

        // Apply kernel redirection rules
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)
        val ipSuccess = executeSu(iptablesCmds)

        return if (ipSuccess) {
            StartResult(success = true)
        } else {
            stopProxy(context, user)
            StartResult(success = false, errorMessage = "Failed to apply iptables rules")
        }
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId
    ): Boolean {
        val commands = mutableListOf<String>()

        // 1. Flush iptables rules across all slots
        for (s in 0..4) {
            commands.addAll(IptablesManager.generateDisableCommands(user, s))
            val pFile = getPidFile(user, s)
            commands.add("if [ -f $pFile ]; then kill -9 \$(cat $pFile) 2>/dev/null; rm -f $pFile; fi")
        }

        // 2. Terminate background processes
        commands.add("rm -f $ACTIVE_SLOT_FILE")
        commands.add("killall -9 sing-box 2>/dev/null")
        commands.add("pkill -9 -f sing-box 2>/dev/null")

        return executeSu(commands)
    }
}
