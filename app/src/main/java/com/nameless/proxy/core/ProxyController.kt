package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

object ProxyController {

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

    fun extractCoreBinary(context: Context): File {
        val binaryFile = File(context.filesDir, "sing-box")
        if (!binaryFile.exists() || binaryFile.length() == 0L) {
            context.assets.open("sing-box").use { input ->
                FileOutputStream(binaryFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        executeSu(listOf("chmod 755 ${binaryFile.absolutePath}"))
        return binaryFile
    }

    fun startProxy(context: Context, settings: ProxySettings, selectedUids: List<Int>? = null): Boolean {
        val binary = extractCoreBinary(context)
        val configFile = File(context.filesDir, "config.json")
        val pidFile = File(context.filesDir, "sing-box.pid")
        val port = ProfileManager.localInboundPort

        // 1. Write the sing-box configuration
        val configContent = ConfigGenerator.generateJson(settings, port)
        configFile.writeText(configContent)

        // 2. Kill existing daemon instance for this profile
        stopProxy(context)

        // 3. Launch sing-box daemon in background under root
        val runCommand = "${binary.absolutePath} run -c ${configFile.absolutePath} & echo \$! > ${pidFile.absolutePath}"
        executeSu(listOf(runCommand))

        // 4. Apply dual-stack or single-stack Netfilter rules
        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings.ipMode, selectedUids)
        return executeSu(iptablesCmds)
    }

    fun stopProxy(context: Context): Boolean {
        val pidFile = File(context.filesDir, "sing-box.pid")
        val commands = mutableListOf<String>()

        if (pidFile.exists()) {
            val pid = pidFile.readText().trim()
            if (pid.isNotEmpty()) {
                commands.add("kill -9 $pid 2>/dev/null")
            }
            pidFile.delete()
        }

        // Flush and restore both IPv4 & IPv6 firewall rules
        commands.addAll(IptablesManager.generateDisableCommands())
        return executeSu(commands)
    }
}
