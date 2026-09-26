package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream

object BootManager {

    private const val SERVICE_DIR = "/data/adb/service.d"
    private const val BOOT_SCRIPT = "$SERVICE_DIR/nameless_boot.sh"
    private const val ADB_DIR = "/data/adb/nameless_proxy"

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

    fun isBootScriptInstalled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("if [ -f $BOOT_SCRIPT ]; then echo 'EXISTS'; fi\n")
            os.writeBytes("exit\n")
            os.flush()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("EXISTS")
        } catch (e: Exception) {
            false
        }
    }

    fun removeBootScript(): Boolean {
        return executeSu(listOf(
            "rm -f $BOOT_SCRIPT"
        ))
    }

    fun syncBootState(
        context: Context,
        startOnBoot: Boolean,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): Boolean {
        if (!startOnBoot) {
            return removeBootScript()
        }

        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val port = ProfileManager.localInboundPort
        val binaryPath = ProxyController.getBinaryPath()
        val configPath = ProxyController.getConfigPath(user, slot)
        val pidFile = ProxyController.getPidFile(user, slot)
        val logFile = ProxyController.getLogFile(user, slot)

        // Write configuration
        val configJson = ConfigGenerator.generateJson(settings, port)
        ProxyController.writeConfigDirectly(configJson, configPath)

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)

        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# Nameless Proxy Boot Service\n")
        sb.append("while [ \"\$(getprop sys.boot_completed)\" != \"1\" ]; do\n")
        sb.append("    sleep 2\n")
        sb.append("done\n\n")

        sb.append("mkdir -p $ADB_DIR\n")
        sb.append("mkdir -p $SERVICE_DIR\n")
        sb.append("chmod 755 $binaryPath\n\n")

        sb.append("# Clean prior rules\n")
        for (cmd in IptablesManager.generateDisableCommands(user, slot)) {
            sb.append("$cmd\n")
        }
        sb.append("killall -9 sing-box 2>/dev/null\n\n")

        sb.append("# Launch daemon\n")
        sb.append("nohup $binaryPath run -c $configPath > $logFile 2>&1 & echo \$! > $pidFile\n")
        sb.append("echo $slot > $ADB_DIR/running_slot\n\n")

        sb.append("# Apply routing\n")
        for (cmd in iptablesCmds) {
            sb.append("$cmd\n")
        }

        val scriptContent = sb.toString()

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $BOOT_SCRIPT && chmod 755 $BOOT_SCRIPT"))
            process.outputStream.write(scriptContent.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            process.outputStream.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
