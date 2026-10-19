package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream

object BootManager {

    private const val SERVICE_DIR = "/data/adb/service.d"
    private const val BOOT_SCRIPT = "$SERVICE_DIR/nameless_boot.sh"
    private const val ADB_DIR = "/data/adb/nameless_proxy"

    private fun executeSu(commands: List<String>): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                }
                os.write("exit\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isBootScriptInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                os.write("if [ -f $BOOT_SCRIPT ]; then echo 'EXISTS'; fi\n".toByteArray(Charsets.UTF_8))
                os.write("exit\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.contains("EXISTS")
        } catch (e: Exception) {
            false
        }
    }

    fun removeBootScript(): Boolean {
        return executeSu(listOf(
            "rm -f $BOOT_SCRIPT",
            "if [ -f $ADB_DIR/saved_private_dns_mode ]; then settings put global private_dns_mode \$(cat $ADB_DIR/saved_private_dns_mode) 2>/dev/null; rm -f $ADB_DIR/saved_private_dns_mode; cmd connectivity flush-dns 2>/dev/null; fi"
        ))
    }

    fun syncBootState(
        context: Context,
        startOnBoot: Boolean,
        settings: ProxySettings,
        selectedUids: List<Int>? = null,
        targetSlot: Int = ProfileManager.activeSlot
    ): Boolean {
        if (!startOnBoot || settings.host.isBlank()) {
            return removeBootScript()
        }

        val user = ProfileManager.androidUserId
        val port = 10800 + (user * 100) + (targetSlot * 10)
        val binaryPath = ProxyController.getBinaryPath()
        val configPath = ProxyController.getConfigPath(user, targetSlot)
        val pidFile = ProxyController.getPidFile(user, targetSlot)
        val logFile = ProxyController.getLogFile(user, targetSlot)
        val slotFile = ProxyController.getActiveSlotFile(user)

        val configJson = ConfigGenerator.generateJson(settings, port)
        ProxyController.writeConfigDirectly(configJson, configPath)

        val previousSlot = ProfileManager.activeSlot
        ProfileManager.activeSlot = targetSlot
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)
        val disableCmds = IptablesManager.generateDisableCommands(user, targetSlot)
        ProfileManager.activeSlot = previousSlot

        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# Nameless Proxy Kernel Boot Service\n\n")
        sb.append("export PATH=/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH:/system/bin:/system/xbin\n\n")

        sb.append("while [ \"\$(getprop sys.boot_completed)\" != \"1\" ]; do\n")
        sb.append("    sleep 2\n")
        sb.append("done\n\n")

        sb.append("count=0\n")
        sb.append("while [ \$count -lt 20 ]; do\n")
        sb.append("    if ip route show table all 2>/dev/null | grep -q \"default\" || ip -6 route show table all 2>/dev/null | grep -q \"default\"; then\n")
        sb.append("        break\n")
        sb.append("    fi\n")
        sb.append("    sleep 1\n")
        sb.append("    count=\$((count + 1))\n")
        sb.append("done\n\n")
        sb.append("sleep 2\n\n")

        sb.append("mkdir -p $ADB_DIR\n")
        sb.append("chmod 755 $binaryPath\n")
        sb.append("chcon u:object_r:system_file:s0 $binaryPath 2>/dev/null\n\n")

        for (cmd in disableCmds) {
            sb.append(cmd).append("\n")
        }
        sb.append("killall -9 sing-box 2>/dev/null\n")
        sb.append("pkill -9 -f sing-box 2>/dev/null\n\n")

        // Private DNS handling for system boot (handles "null" factory default properly)
        sb.append("cur_dns=\$(settings get global private_dns_mode 2>/dev/null)\n")
        sb.append("if [ -z \"\$cur_dns\" ] || [ \"\$cur_dns\" = \"null\" ]; then\n")
        sb.append("    echo \"opportunistic\" > $ADB_DIR/saved_private_dns_mode\n")
        sb.append("elif [ \"\$cur_dns\" != \"off\" ]; then\n")
        sb.append("    echo \"\$cur_dns\" > $ADB_DIR/saved_private_dns_mode\n")
        sb.append("fi\n")
        sb.append("settings put global private_dns_mode off 2>/dev/null\n")
        sb.append("cmd connectivity flush-dns 2>/dev/null || true\n\n")

        sb.append("( trap '' HUP; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile )\n")
        sb.append("PID=\$(cat $pidFile 2>/dev/null)\n")
        sb.append("echo $targetSlot > $slotFile\n")
        sb.append("echo $targetSlot > $ADB_DIR/running_slot\n\n")

        sb.append("sleep 2\n")
        sb.append("if [ -n \"\$PID\" ] && kill -0 \$PID 2>/dev/null; then\n")
        for (cmd in iptablesCmds) {
            sb.append("    ").append(cmd).append("\n")
        }
        sb.append("else\n")
        sb.append("    echo \"[!] sing-box daemon failed to start on boot\" >> $logFile\n")
        sb.append("fi\n")

        val scriptContent = sb.toString()

        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $SERVICE_DIR && cat > $BOOT_SCRIPT && chmod 755 $BOOT_SCRIPT"
            ).redirectErrorStream(true).start()
            process.outputStream.use { os ->
                os.write(scriptContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
