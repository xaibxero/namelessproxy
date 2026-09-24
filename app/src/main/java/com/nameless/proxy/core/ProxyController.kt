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

    // Stream APK asset directly into /data/local/tmp/sing-box via root stdin
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

    // Stream configuration directly into /data/local/tmp via root stdin
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

    fun getDiagnosticsAndLogs(): String {
        val profileId = ProfileManager.profileId
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"
        return executeSuWithOutput(listOf(
            "echo '--- BINARY STATUS ---'",
            "ls -la /data/local/tmp/sing-box 2>&1",
            "/data/local/tmp/sing-box version 2>&1 | head -n 3",
            "echo ''",
            "echo '--- RECENT LOGS ---'",
            "if [ -f $logFile ]; then tail -n 25 $logFile; else echo 'No log file generated yet.'; fi"
        ))
    }

    fun startProxy(context: Context, settings: ProxySettings, selectedUids: List<Int>? = null): StartResult {
        val binaryPath = "/data/local/tmp/sing-box"
        val profileId = ProfileManager.profileId
        val configPath = "/data/local/tmp/singbox_u${profileId}.json"
        val pidFile = "/data/local/tmp/singbox_u${profileId}.pid"
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"
        val port = ProfileManager.localInboundPort

        // 1. Stream sing-box binary to destination if missing
        val binaryCheck = executeSuWithOutput(listOf("if [ -f $binaryPath ] && [ -x $binaryPath ]; then echo 'EXISTS'; fi"))
        if (!binaryCheck.contains("EXISTS")) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted) {
                return StartResult(success = false, errorMessage = "Failed to stream sing-box binary to $binaryPath")
            }
        }

        // 2. Stream generated configuration directly
        val configContent = ConfigGenerator.generateJson(settings, port)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write configuration to $configPath")
        }

        // 3. Terminate any previous instance
        stopProxy(context)

        // 4. Launch sing-box daemon in background under root
        val runCmd = "nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile"
        executeSu(listOf(runCmd))

        // 5. Wait 600ms and verify process health
        Thread.sleep(600)
        if (!isRunning()) {
            val failureInfo = getDiagnosticsAndLogs()
            return StartResult(
                success = false,
                errorMessage = failureInfo.ifEmpty { "sing-box failed to start or crashed" }
            )
        }

        // 6. Apply Netfilter redirection rules
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings.ipMode, selectedUids)
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
