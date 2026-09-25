package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.io.File

object BootManager {

    private const val ADB_DIR = "/data/adb/nameless_proxy"
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
        } catch (e: Exception) {
            false
        }
    }

    fun removeBootScript(): Boolean {
        return executeSu(listOf(
            "rm -f $SERVICE_SCRIPT_PATH",
            "rm -f /data/adb/service.d/nameless_proxy_*.sh"
        ))
    }

    fun syncBootState(
        context: Context,
        enabled: Boolean,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): Boolean {
        if (!enabled) {
            return removeBootScript()
        }

        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val port = ProfileManager.localInboundPort

        val binaryPath = "$ADB_DIR/sing-box"
        val configPath = "$ADB_DIR/config_u${user}_s${slot}.json"
        val pidFile = "$ADB_DIR/singbox_u${user}_s${slot}.pid"
        val logFile = "$ADB_DIR/singbox_u${user}_s${slot}.log"

        val configJson = ConfigGenerator.generateJson(settings, port)
        ProxyController.writeConfigDirectly(configJson, configPath)

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)

        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Nameless Proxy Fast Boot Script")
            appendLine("export PATH=/system/bin:/system/xbin:\$PATH")
            appendLine("")
            appendLine("i=0")
            appendLine("while [ \$i -lt 30 ]; do")
            appendLine("  if ip link show lo 2>/dev/null | grep -q \"UP\"; then")
            appendLine("    break")
            appendLine("  fi")
            appendLine("  sleep 0.1")
            appendLine("  i=\$((i+1))")
            appendLine("done")
            appendLine("")
            appendLine("mkdir -p $ADB_DIR")
            appendLine("chmod 755 $binaryPath 2>/dev/null")
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
            appendLine("")
            for (cmd in iptablesCmds) {
                appendLine(cmd)
            }
        }

        val tempScript = File(context.cacheDir, "boot_service.sh")
        tempScript.writeText(scriptContent)

        return executeSu(listOf(
            "mkdir -p /data/adb/service.d",
            "mkdir -p $ADB_DIR",
            "cp ${tempScript.absolutePath} $SERVICE_SCRIPT_PATH",
            "chmod 755 $SERVICE_SCRIPT_PATH",
            "rm -f ${tempScript.absolutePath}"
        ))
    }
}
