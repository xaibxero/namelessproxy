package com.nameless.proxy.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object PersistentStorage {

    private const val ADB_DIR = "/data/adb/nameless_proxy"

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
            ""
        }
    }

    fun saveBackup(
        context: Context,
        slot: Int,
        settings: ProxySettings,
        startOnBoot: Boolean,
        routeWholeProfile: Boolean,
        selectedPackages: Set<String>,
        userId: Int = ProfileManager.androidUserId
    ): Boolean {
        return try {
            val backupFile = "$ADB_DIR/backup_u${userId}_s${slot}.json"
            val json = JSONObject().apply {
                put("slot", slot)
                put("proxy_type", settings.type.name)
                put("transport_mode", settings.transportMode.name)
                put("ip_mode", settings.ipMode.name)
                put("host", settings.host)
                put("port", settings.port.toString())
                put("username", settings.username)
                put("password", settings.password)
                put("sni", settings.sni)
                put("ss_method", settings.ssMethod)
                put("reality_pk", settings.realityPublicKey)
                put("reality_sid", settings.realityShortId)
                put("route_hotspot", settings.routeHotspot)
                put("start_on_boot", startOnBoot)
                put("route_whole_profile", routeWholeProfile)
                put("selected_packages", JSONArray(selectedPackages))
            }
            ProxyController.writeConfigDirectly(json.toString(2), backupFile)
        } catch (e: Exception) {
            false
        }
    }

    fun loadBackup(slot: Int, userId: Int = ProfileManager.androidUserId): JSONObject? {
        val backupFile = "$ADB_DIR/backup_u${userId}_s${slot}.json"
        val raw = executeSuWithOutput(listOf("if [ -f $backupFile ]; then cat $backupFile; fi"))
        return if (raw.isNotEmpty() && raw.startsWith("{")) {
            try {
                JSONObject(raw)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
