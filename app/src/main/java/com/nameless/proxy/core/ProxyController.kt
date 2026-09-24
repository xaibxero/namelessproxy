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

    fun isRunning(): Boolean {
        val pidFile = "/data/local/tmp/singbox_u${ProfileManager.profileId}.pid"
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun clearLogs() {
        val logFile = "/data/local/tmp/singbox_u${ProfileManager.profileId}.log"
        executeSu(listOf("> $logFile"))
    }

    fun getDiagnosticsAndLogs(): String {
        val profileId = ProfileManager.profileId
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"
        val raw = executeSuWithOutput(listOf(
            "echo '=== DAEMON STATUS ==='",
            "if [ -f /data/local/tmp/singbox_u${profileId}.pid ] && kill -0 \$(cat /data/local/tmp/singbox_u${profileId}.pid) 2>/dev/null; then",
            "  echo 'State: ACTIVE (PID: '\$(cat /data/local/tmp/singbox_u${profileId}.pid)')'",
            "else",
            "  echo 'State: STOPPED / NOT RUNNING'",
            "fi",
            "echo ''",
            "echo '=== RECENT LOG ENTRIES ==='",
            "if [ -f $logFile ]; then tail -n 120 $logFile; else echo 'No log file found.'; fi"
        ))
        // Strip raw ANSI terminal color codes for clear reading
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun startProxy(context: Context, settings: ProxySettings, selectedUids: List<Int>? = null): StartResult {
        val binaryPath = "/data/local/tmp/sing-box"
        val profileId = ProfileManager.profileId
        val configPath = "/data/local/tmp/singbox_u${profileId}.json"
        val pidFile = "/data/local/tmp/singbox_u${profileId}.pid"
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"
        val port = ProfileManager.localInboundPort

        // Verify executable status
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

        stopProxy(context)

        val runCmd = "nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile"
        executeSu(listOf(runCmd))

        Thread.sleep(600)
        if (!isRunning()) {
            val failureInfo = getDiagnosticsAndLogs()
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
            stopProxy(context)
            StartResult(success = false, errorMessage = "Failed to apply iptables rules")
        }
    }

    fun stopProxy(context: Context): Boolean {
        val profileId = ProfileManager.profileId
        val pidFile = "/data/local/tmp/singbox_u${profileId}.pid"
        val commands = mutableListOf<String>()

        commands.add("if [ -f $pidFile ]; then kill -9 \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi")
        commands.addAll(IptablesManager.generateDisableCommands())

        return executeSu(commands)
    }
}
