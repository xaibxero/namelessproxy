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
            e.printStackTrace()
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
            e.printStackTrace()
            false
        }
    }

    fun extractBinaryDirectly(context: Context): Boolean {
        val targetPath = "/data/local/tmp/sing-box"
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $targetPath && cat > $targetPath && chmod 755 $targetPath"))
            context.assets.open("sing-box").use { input ->
                input.copyTo(process.outputStream)
            }
            process.outputStream.flush()
            process.outputStream.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeConfigDirectly(configContent: String, configPath: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $configPath && cat > $configPath && chmod 644 $configPath"))
            process.outputStream.write(configContent.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            process.outputStream.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isRunning(slot: Int = ProfileManager.profileId): Boolean {
        val pidFile = "/data/local/tmp/singbox_u$slot.pid"
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getActivePid(slot: Int = ProfileManager.profileId): String? {
        val pidFile = "/data/local/tmp/singbox_u$slot.pid"
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then cat $pidFile; fi"
        ))
        return result.trim().ifEmpty { null }
    }

    fun clearLogs(slot: Int = ProfileManager.profileId) {
        val logFile = "/data/local/tmp/singbox_u$slot.log"
        executeSu(listOf("> $logFile"))
    }

    fun getDiagnosticsAndLogs(slot: Int = ProfileManager.profileId): String {
        val logFile = "/data/local/tmp/singbox_u$slot.log"
        val raw = executeSuWithOutput(listOf(
            "echo '=== DAEMON STATUS (SLOT $slot) ==='",
            "if [ -f /data/local/tmp/singbox_u$slot.pid ] && kill -0 \$(cat /data/local/tmp/singbox_u$slot.pid) 2>/dev/null; then",
            "  echo 'State: ACTIVE (PID: '\$(cat /data/local/tmp/singbox_u$slot.pid)')'",
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
        val slot = ProfileManager.profileId
        val binaryPath = "/data/local/tmp/sing-box"
        val configPath = "/data/local/tmp/singbox_u$slot.json"
        val pidFile = "/data/local/tmp/singbox_u$slot.pid"
        val logFile = "/data/local/tmp/singbox_u$slot.log"
        val port = ProfileManager.localInboundPort

        val testRun = executeSuWithOutput(listOf("$binaryPath version 2>&1"))
        if (!testRun.contains("sing-box version")) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted) {
                return StartResult(success = false, errorMessage = "Failed to extract core binary")
            }
        }

        val configContent = ConfigGenerator.generateJson(settings, port)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        // Clean any active rules across all slots before starting
        stopAll(context)

        val runCmd = "nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile"
        executeSu(listOf(runCmd))

        Thread.sleep(700)
        if (!isRunning(slot)) {
            val failureInfo = getDiagnosticsAndLogs(slot)
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
            stopProxy(context, slot)
            StartResult(success = false, errorMessage = "Failed to apply iptables rules")
        }
    }

    fun stopProxy(context: Context, slot: Int = ProfileManager.profileId): Boolean {
        val pidFile = "/data/local/tmp/singbox_u$slot.pid"
        val commands = mutableListOf<String>()

        commands.add("if [ -f $pidFile ]; then kill -9 \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi")
        commands.addAll(IptablesManager.generateDisableCommands(slot))

        return executeSu(commands)
    }

    fun stopAll(context: Context): Boolean {
        val commands = mutableListOf<String>()
        for (i in 0 until ProfileManager.MAX_PROFILES) {
            val pidFile = "/data/local/tmp/singbox_u$i.pid"
            commands.add("if [ -f $pidFile ]; then kill -9 \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi")
        }
        commands.addAll(IptablesManager.generateDisableAllCommands())
        return executeSu(commands)
    }
}
