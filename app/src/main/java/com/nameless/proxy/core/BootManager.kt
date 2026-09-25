package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.io.File

object BootManager {

    private fun getServiceScriptPath(user: Int, slot: Int) =
        "/data/adb/service.d/nameless_proxy_u${user}_s${slot}.sh"

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

    fun removeBootScript(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): Boolean {
        return executeSu(listOf("rm -f ${getServiceScriptPath(user, slot)}"))
    }

    fun syncBootState(
        context: Context,
        enabled: Boolean,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): Boolean {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val scriptPath = getServiceScriptPath(user, slot)

        if (!enabled) {
            return removeBootScript(user, slot)
        }

        val port = ProfileManager.localInboundPort
        val binaryPath = "/data/local/tmp/sing-box"
        val configPath = ProxyController.getConfigPath(user, slot)
        val pidFile = ProxyController.getPidFile(user, slot)
        val logFile = ProxyController.getLogFile(user, slot)

        val configJson = ConfigGenerator.generateJson(settings, port)
        ProxyController.writeConfigDirectly(configJson, configPath)

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)

        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Nameless Proxy Fast Boot Script (User $user • Slot $slot)")
            appendLine("sleep 5")
            appendLine("export PATH=/system/bin:/system/xbin:\$PATH")
            appendLine("")
            appendLine("if [ ! -f $binaryPath ] || [ ! -f $configPath ]; then")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine("")
            appendLine("if [ -f $pidFile ]; then")
            appendLine("  kill -9 \$(cat $pidFile) 2>/dev/null")
            appendLine("  rm -f $pidFile")
            appendLine("fi")
            appendLine("")
            appendLine("nohup $binaryPath run -c $configPath > $logFile 2>&1 &")
            appendLine("echo \$! > $pidFile")
            appendLine("sleep 1")
            appendLine("")
            for (cmd in iptablesCmds) {
                appendLine(cmd)
            }
        }

        val tempScript = File(context.cacheDir, "boot_service_u${user}_s${slot}.sh")
        tempScript.writeText(scriptContent)

        return executeSu(listOf(
            "mkdir -p /data/adb/service.d",
            "cp ${tempScript.absolutePath} $scriptPath",
            "chmod 755 $scriptPath",
            "rm -f ${tempScript.absolutePath}"
        ))
    }
}
