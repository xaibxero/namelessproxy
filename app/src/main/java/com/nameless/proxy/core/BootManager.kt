package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.io.File

object BootManager {

    private const val SERVICE_SCRIPT_PATH = "/data/adb/service.d/nameless_proxy.sh"

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
        } catch (_: Exception) {
            false
        }
    }

    fun syncBootState(
        context: Context,
        enabled: Boolean,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): Boolean {
        if (!enabled) {
            return executeSu(listOf("rm -f $SERVICE_SCRIPT_PATH"))
        }

        val profileId = ProfileManager.profileId
        val port = ProfileManager.localInboundPort
        val binaryPath = "/data/local/tmp/sing-box"
        val configPath = "/data/local/tmp/singbox_u${profileId}.json"
        val pidFile = "/data/local/tmp/singbox_u${profileId}.pid"
        val logFile = "/data/local/tmp/singbox_u${profileId}.log"

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)

        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Nameless Proxy Early Boot Service (KernelSU / Magisk / APatch)")
            appendLine("sleep 5")
            appendLine("if [ -f $pidFile ]; then kill -9 \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi")
            appendLine("nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile")
            appendLine("sleep 1")
            for (cmd in iptablesCmds) {
                appendLine(cmd)
            }
        }

        val tempScript = File(context.cacheDir, "boot_service.sh")
        tempScript.writeText(scriptContent)

        return executeSu(listOf(
            "mkdir -p /data/adb/service.d",
            "cp ${tempScript.absolutePath} $SERVICE_SCRIPT_PATH",
            "chmod 755 $SERVICE_SCRIPT_PATH",
            "rm -f ${tempScript.absolutePath}"
        ))
    }
}
