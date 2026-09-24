package com.nameless.proxy.core

import android.content.Context
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
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

    // Unpack sing-box into /data/local/tmp where execution is universally permitted
    fun prepareBinary(context: Context): String {
        val targetPath = "/data/local/tmp/sing-box"
        val tempFile = File(context.cacheDir, "sing-box-temp")

        context.assets.open("sing-box").use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        executeSu(listOf(
            "cp ${tempFile.absolutePath} $targetPath",
            "chmod 755 $targetPath",
            "rm -f ${tempFile.absolutePath}"
        ))

        return targetPath
    }

    fun isRunning(): Boolean {
        val pidFile = "/data/local/tmp/singbox_u${ProfileManager.profileId}.pid"
        val result = executeSuWithOutput(listOf(
            "if [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getRecentLogs(): String {
        val logFile = "/data/local/tmp/singbox_u${ProfileManager.profileId}.log"
        return executeSuWithOutput(listOf("tail -n 25 $logFile 2>/dev/null"))
    }

    fun startProxy(context: Context, settings: ProxySettings, selectedUids: List<Int>? = null): StartResult {
        val binaryPath = prepareBinary(context)
        val profileId = ProfileManager.profileId
        val configPath = "/data/local/tmp/singbox_u${profileId}.json"
        val pidFile = "/data/local/tmp/singbox_u${profileId}.pid"
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"
        val port = ProfileManager.localInboundPort

        // 1. Write the sing-box config
        val configContent = ConfigGenerator.generateJson(settings, port)
        val tempConfig = File(context.cacheDir, "config-temp.json")
        tempConfig.writeText(configContent)

        executeSu(listOf(
            "cp ${tempConfig.absolutePath} $configPath",
            "chmod 644 $configPath",
            "rm -f ${tempConfig.absolutePath}"
        ))

        // 2. Kill existing instance
        stopProxy(context)

        // 3. Launch sing-box with nohup & setsid so it survives su session termination
        val runCmd = "nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile"
        executeSu(listOf(runCmd))

        // 4. Wait 600ms and verify process health
        Thread.sleep(600)
        if (!isRunning()) {
            val failureLog = getRecentLogs()
            return StartResult(
                success = false,
                errorMessage = if (failureLog.isNotEmpty()) failureLog else "sing-box failed to start or crashed"
            )
        }

        // 5. Apply iptables redirection rules
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
