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

        // Write configuration for the boot profile
        val configJson = ConfigGenerator.generateJson(settings, port)
        ProxyController.writeConfigDirectly(configJson, configPath)

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)
        val disableCmds = IptablesManager.generateDisableCommands(user, slot)

        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# Nameless Proxy Kernel Boot Service\n\n")
        sb.append("export PATH=/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH:/system/bin:/system/xbin\n\n")

        // 1. Wait for system boot completed
        sb.append("while [ \"\$(getprop sys.boot_completed)\" != \"1\" ]; do\n")
        sb.append("    sleep 2\n")
        sb.append("done\n\n")

        // 2. Wait up to 12s for active network route
        sb.append("count=0\n")
        sb.append("while [ \$count -lt 12 ]; do\n")
        sb.append("    if ip route | grep -q \"default\" || ip -6 route | grep -q \"default\"; then\n")
        sb.append("        break\n")
        sb.append("    fi\n")
        sb.append("    sleep 1\n")
        sb.append("    count=\$((count + 1))\n")
        sb.append("done\n\n")
        sb.append("sleep 2\n\n")

        sb.append("mkdir -p $ADB_DIR\n")
        sb.append("chmod 755 $binaryPath\n")
        sb.append("chcon u:object_r:system_file:s0 $binaryPath 2>/dev/null\n\n")

        // 3. Clean prior instances and rules
        for (i in 0 until disableCmds.size) {
            sb.append(disableCmds[i]).append("\n")
        }
        sb.append("killall -9 sing-box 2>/dev/null\n")
        sb.append("pkill -9 -f sing-box 2>/dev/null\n\n")

        // 4. Launch sing-box natively in background without nohup
        sb.append("$binaryPath run -c $configPath > $logFile 2>&1 < /dev/null &\n")
        sb.append("PID=\$!\n")
        sb.append("echo \$PID > $pidFile\n")
        sb.append("echo $slot > $ADB_DIR/running_slot\n\n")

        // 5. Verify sing-box is alive before applying iptables
        sb.append("sleep 1.5\n")
        sb.append("if kill -0 \$PID 2>/dev/null; then\n")
        for (i in 0 until iptablesCmds.size) {
            sb.append("    ").append(iptablesCmds[i]).append("\n")
        }
        sb.append("else\n")
        sb.append("    echo \"[!] sing-box daemon failed to start on boot\" >> $logFile\n")
        sb.append("fi\n")

        val scriptContent = sb.toString()

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $SERVICE_DIR && cat > $BOOT_SCRIPT && chmod 755 $BOOT_SCRIPT"))
            process.outputStream.write(scriptContent.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            process.outputStream.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
