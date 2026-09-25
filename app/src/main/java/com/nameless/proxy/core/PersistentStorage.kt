package com.nameless.proxy.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File

object PersistentStorage {

    private const val BACKUP_DIR = "/data/adb/nameless_proxy"

    private fun getBackupPath(profileId: Int): String {
        return "$BACKUP_DIR/profile_${profileId}_config.json"
    }

    private fun executeSu(commands: List<String>): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun saveBackup(
        context: Context,
        profileId: Int,
        settings: ProxySettings,
        startOnBoot: Boolean,
        routeWholeProfile: Boolean,
        selectedPackages: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("proxy_type", settings.type.name)
                put("transport_mode", settings.transportMode.name)
                put("ip_mode", settings.ipMode.name)
                put("host", settings.host)
                put("port", settings.port.toString())
                put("username", settings.username)
                put("password", settings.password)
                put("start_on_boot", startOnBoot)
                put("route_whole_profile", routeWholeProfile)
                put("route_hotspot", settings.routeHotspot)
                put("sni", settings.sni)
                put("ss_method", settings.ssMethod)
                put("reality_public_key", settings.realityPublicKey)
                put("reality_short_id", settings.realityShortId)
                put("selected_packages", JSONArray(selectedPackages))
            }

            val tempFile = File(context.cacheDir, "temp_p${profileId}_backup.json")
            tempFile.writeText(json.toString(2))
            val targetPath = getBackupPath(profileId)

            executeSu(listOf(
                "mkdir -p $BACKUP_DIR",
                "cp ${tempFile.absolutePath} $targetPath",
                "chmod 600 $targetPath",
                "rm -f ${tempFile.absolutePath}"
            ))
        } catch (_: Exception) {}
    }

    suspend fun loadBackup(profileId: Int): JSONObject? = withContext(Dispatchers.IO) {
        val targetPath = getBackupPath(profileId)
        val raw = executeSu(listOf(
            "if [ -f $targetPath ]; then cat $targetPath; fi"
        )).trim()

        if (raw.isEmpty()) return@withContext null
        try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }
}
