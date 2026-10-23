package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.net.InetAddress

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
                os.write("exit 0\n".toByteArray(Charsets.UTF_8))
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
                os.write("exit 0\n".toByteArray(Charsets.UTF_8))
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
        return executeSu(listOf("rm -f $BOOT_SCRIPT"))
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

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val resolvedHost = if (!IptablesManager.isValidIpv4(rawHost) && !IptablesManager.isValidIpv6(rawHost)) {
            try {
                InetAddress.getByName(rawHost).hostAddress ?: rawHost
            } catch (_: Exception) {
                rawHost
            }
        } else {
            rawHost
        }

        val previousSlot = ProfileManager.activeSlot
        ProfileManager.activeSlot = targetSlot
        val configJson = ConfigGenerator.generateJson(settings, port, resolvedHost)
        ProxyController.writeConfigDirectly(configJson, configPath)
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids, resolvedHost, skipInitialClean = true)
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
        sb.append("killall -9 sing-box 2>/dev/null || true\n")
        for (cmd in disableCmds) {
            sb.append(cmd).append("\n")
        }
        sb.append("rm -f $ADB_DIR/cache_u${user}_s${targetSlot}.db\n")
        sb.append("echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true\n")
        sb.append("( trap '' HUP; $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile )\n")
        sb.append("echo $targetSlot > $slotFile\n")
        sb.append("echo $targetSlot > $ADB_DIR/running_slot\n")
        sb.append("sleep 1\n\n")

        for (cmd in iptablesCmds) {
            sb.append(cmd).append("\n")
        }

        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $SERVICE_DIR && rm -f $BOOT_SCRIPT && cat > $BOOT_SCRIPT && chmod 755 $BOOT_SCRIPT"
            ).redirectErrorStream(true).start()
            process.outputStream.use { os ->
                os.write(sb.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
