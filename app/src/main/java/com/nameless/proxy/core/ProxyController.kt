package com.nameless.proxy.core

import android.content.Context
import java.io.DataOutputStream

data class StartResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object ProxyController {

    private const val ADB_DIR = "/data/adb/nameless_proxy"

    fun getBinaryPath(): String = ADB_DIR + "/sing-box"

    fun getActiveSlotFile(user: Int): String =
        ADB_DIR + "/running_slot_u" + user

    fun getConfigPath(user: Int, slot: Int): String =
        ADB_DIR + "/config_u" + user + "_s" + slot + ".json"

    fun getPidFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".pid"

    fun getLogFile(user: Int, slot: Int): String =
        ADB_DIR + "/singbox_u" + user + "_s" + slot + ".log"

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                }
                os.write("exit\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

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

    fun getRunningSlot(user: Int = ProfileManager.androidUserId): Int? {
        val slotFile = getActiveSlotFile(user)
        val legacyFile = ADB_DIR + "/running_slot"
        val cmd = if (user == 0) {
            "if [ -f " + slotFile + " ]; then cat " + slotFile + "; elif [ -f " + legacyFile + " ]; then cat " + legacyFile + "; fi"
        } else {
            "if [ -f " + slotFile + " ]; then cat " + slotFile + "; fi"
        }
        val out = executeSuWithOutput(listOf(cmd)).lineSequence().lastOrNull()?.trim() ?: ""
        val s = out.toIntOrNull()
        return if (s != null && isRunning(user, s)) s else null
    }

    fun extractBinaryDirectly(context: Context): Boolean {
        val targetPath = getBinaryPath()
        return try {
            val assetStream = context.assets.open("sing-box")

            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p $ADB_DIR && cat > $targetPath && chmod 755 $targetPath && chcon u:object_r:system_file:s0 $targetPath 2>/dev/null || true"
            ).redirectErrorStream(true).start()

            assetStream.use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (process.waitFor() != 0) return false

            val checkVer = executeSuWithOutput(listOf("$targetPath version 2>&1"))
            checkVer.contains("sing-box")
        } catch (e: Exception) {
            false
        }
    }

    fun writeConfigDirectly(configContent: String, configPath: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p " + ADB_DIR + " && rm -f " + configPath + " && cat > " + configPath + " && chmod 644 " + configPath
            ).redirectErrorStream(true).start()
            process.outputStream.use { os ->
                os.write(configContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isRunning(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): Boolean {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then echo 'RUNNING'; else echo 'STOPPED'; fi"
        ))
        return result.contains("RUNNING")
    }

    fun getActivePid(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String? {
        val pidFile = getPidFile(user, slot)
        val result = executeSuWithOutput(listOf(
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then cat " + pidFile + "; fi"
        ))
        return result.lineSequence().lastOrNull()?.trim()?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
    }

    fun clearLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot) {
        val logFile = getLogFile(user, slot)
        executeSu(listOf(": > " + logFile))
    }

    fun getDiagnosticsAndLogs(user: Int = ProfileManager.androidUserId, slot: Int = ProfileManager.activeSlot): String {
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val raw = executeSuWithOutput(listOf(
            "echo '=== DAEMON STATUS (USER " + user + " • SLOT P" + slot + ") ==='",
            "if [ -f " + pidFile + " ] && kill -0 $(cat " + pidFile + ") 2>/dev/null; then",
            "  echo 'State: ACTIVE (PID: '$(cat " + pidFile + ")')'",
            "else",
            "  echo 'State: STOPPED / NOT RUNNING'",
            "fi",
            "echo ''",
            "echo '=== RECENT LOG ENTRIES ==='",
            "if [ -f " + logFile + " ]; then tail -n 120 " + logFile + "; else echo 'No log file found.'; fi"
        ))
        return raw.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun startProxy(
        context: Context,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): StartResult {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val binaryPath = getBinaryPath()
        val configPath = getConfigPath(user, slot)
        val pidFile = getPidFile(user, slot)
        val logFile = getLogFile(user, slot)
        val slotFile = getActiveSlotFile(user)
        val port = ProfileManager.localInboundPort

        val testRun = executeSuWithOutput(listOf(binaryPath + " version 2>&1"))
        if (!testRun.contains("sing-box")) {
            val extracted = extractBinaryDirectly(context)
            if (!extracted) {
                return StartResult(success = false, errorMessage = "Failed to extract sing-box binary to " + ADB_DIR)
            }
        }

        val configContent = ConfigGenerator.generateJson(settings, port)
        val configWritten = writeConfigDirectly(configContent, configPath)
        if (!configWritten) {
            return StartResult(success = false, errorMessage = "Failed to write sing-box config")
        }

        stopProxy(context, user)

        // Save active Private DNS mode and switch to 'off' so Android doesn't leak or report 'No internet'
        val curDnsMode = executeSuWithOutput(listOf("settings get global private_dns_mode")).trim()
        if (curDnsMode.isNotEmpty() && curDnsMode != "null") {
            executeSu(listOf(
                "echo '$curDnsMode' > $ADB_DIR/saved_private_dns_mode",
                "settings put global private_dns_mode off",
                "cmd connectivity flush-dns 2>/dev/null || true"
            ))
        }

        val runCmd = "nohup $binaryPath run -c $configPath > $logFile 2>&1 < /dev/null & echo \$! > $pidFile && echo $slot > $slotFile"
        executeSu(listOf(runCmd))

        var alive = false
        for (attempt in 1..6) {
            Thread.sleep(150)
            if (isRunning(user, slot)) {
                alive = true
                if (attempt >= 2) break
            }
        }

        if (!alive || !isRunning(user, slot)) {
            val failureInfo = getDiagnosticsAndLogs(user, slot)
            return StartResult(
                success = false,
                errorMessage = failureInfo.ifEmpty { "sing-box daemon failed to start" }
            )
        }

        val iptablesCmds = IptablesManager.generateEnableCommands(port, settings, selectedUids)
        val ipSuccess = executeSu(iptablesCmds)

        return if (ipSuccess) {
            StartResult(success = true)
        } else {
            stopProxy(context, user)
            StartResult(success = false, errorMessage = "Failed to apply kernel iptables rules")
        }
    }

    fun stopProxy(
        context: Context,
        user: Int = ProfileManager.androidUserId
    ): Boolean {
        val commands = mutableListOf<String>()

        for (s in 0 until ProfileManager.MAX_SLOTS) {
            commands.addAll(IptablesManager.generateDisableCommands(user, s))
            val pFile = getPidFile(user, s)
            commands.add("if [ -f " + pFile + " ]; then kill -9 $(cat " + pFile + ") 2>/dev/null; rm -f " + pFile + "; fi")
        }

        commands.add("rm -f " + getActiveSlotFile(user))
        if (user == 0) {
            commands.add("rm -f " + ADB_DIR + "/running_slot")
        }
        commands.add("pkill -9 -f 'config_u" + user + "_' 2>/dev/null")

        // Restore original Private DNS mode if previously saved
        commands.add("if [ -f $ADB_DIR/saved_private_dns_mode ]; then settings put global private_dns_mode \$(cat $ADB_DIR/saved_private_dns_mode) 2>/dev/null; rm -f $ADB_DIR/saved_private_dns_mode; cmd connectivity flush-dns 2>/dev/null; fi")

        return executeSu(commands)
    }
}
