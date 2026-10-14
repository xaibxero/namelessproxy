package com.nameless.proxy.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream

object PersistentStorage {

    private const val ADB_DIR = "/data/adb/nameless_proxy"
    const val PUBLIC_BACKUP_PATH = "/sdcard/Download/nameless_backup.json"

    private fun executeSuWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
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
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
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
                put("enable_kill_switch", settings.enableKillSwitch)
                put("enable_bbr_tfo", settings.enableBbrAndTfo)
                put("enable_tls_fragment", settings.enableTlsFragment)
                put("hide_hotspot_ttl", settings.hideHotspotTtl)
                put("enable_watchdog", settings.enableWatchdog)
                put("relay_slot", settings.relaySlot)
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
        val jsonStart = raw.indexOf('{')
        return if (jsonStart >= 0) {
            try {
                JSONObject(raw.substring(jsonStart))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearBackup(slot: Int, userId: Int = ProfileManager.androidUserId): Boolean {
        val backupFile = "$ADB_DIR/backup_u${userId}_s${slot}.json"
        val configFile = ProxyController.getConfigPath(userId, slot)
        return executeSu(listOf(
            "rm -f $backupFile",
            "rm -f $configFile"
        ))
    }

    // Export all 5 slots + global config to /sdcard/Download/nameless_backup.json
    fun exportAllToDownloads(context: Context, userId: Int = ProfileManager.androidUserId): Boolean {
        return try {
            val rootObj = JSONObject()
            val globalPrefs = context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
            rootObj.put("version", 2)
            rootObj.put("start_on_boot", globalPrefs.getBoolean("start_on_boot", false))
            rootObj.put("boot_slot", globalPrefs.getInt("boot_slot", 0))
            rootObj.put("route_hotspot", globalPrefs.getBoolean("route_hotspot", true))
            rootObj.put("enable_kill_switch", globalPrefs.getBoolean("enable_kill_switch", false))
            rootObj.put("enable_bbr_tfo", globalPrefs.getBoolean("enable_bbr_tfo", true))
            rootObj.put("enable_tls_fragment", globalPrefs.getBoolean("enable_tls_fragment", false))
            rootObj.put("hide_hotspot_ttl", globalPrefs.getBoolean("hide_hotspot_ttl", true))
            rootObj.put("enable_watchdog", globalPrefs.getBoolean("enable_watchdog", true))

            val slotsArr = JSONArray()
            for (s in 0 until ProfileManager.MAX_SLOTS) {
                val sp = context.getSharedPreferences("nameless_slot_$s", Context.MODE_PRIVATE)
                val slotObj = JSONObject().apply {
                    put("slot", s)
                    put("proxy_type", sp.getString("proxy_type", "SOCKS5"))
                    put("transport_mode", sp.getString("transport_mode", "TCP_AND_UDP"))
                    put("ip_mode", sp.getString("ip_mode", "IPV4_ONLY"))
                    put("host", sp.getString("host", ""))
                    put("port", sp.getString("port", "1080"))
                    put("username", sp.getString("username", ""))
                    put("password", sp.getString("password", ""))
                    put("sni", sp.getString("sni", ""))
                    put("ss_method", sp.getString("ss_method", "2022-blake3-aes-128-gcm"))
                    put("reality_pk", sp.getString("reality_pk", ""))
                    put("reality_sid", sp.getString("reality_sid", ""))
                    put("relay_slot", sp.getInt("relay_slot", -1))
                    put("route_whole_profile", sp.getBoolean("route_whole_profile", true))
                    put("selected_packages", JSONArray(sp.getStringSet("selected_packages", emptySet()) ?: emptySet<String>()))
                }
                slotsArr.put(slotObj)
            }
            rootObj.put("slots", slotsArr)

            val process = ProcessBuilder(
                "su", "-c",
                "cat > $PUBLIC_BACKUP_PATH && chmod 666 $PUBLIC_BACKUP_PATH"
            ).redirectErrorStream(true).start()
            process.outputStream.use { os ->
                os.write(rootObj.toString(2).toByteArray(Charsets.UTF_8))
                os.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    // Import all 5 slots + global config from /sdcard/Download/nameless_backup.json
    fun importAllFromDownloads(context: Context): Boolean {
        return try {
            val raw = executeSuWithOutput(listOf("if [ -f $PUBLIC_BACKUP_PATH ]; then cat $PUBLIC_BACKUP_PATH; fi"))
            val idx = raw.indexOf('{')
            if (idx < 0) return false
            val rootObj = JSONObject(raw.substring(idx))

            val globalPrefs = context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
            globalPrefs.edit()
                .putBoolean("start_on_boot", rootObj.optBoolean("start_on_boot", false))
                .putInt("boot_slot", rootObj.optInt("boot_slot", 0))
                .putBoolean("route_hotspot", rootObj.optBoolean("route_hotspot", true))
                .putBoolean("enable_kill_switch", rootObj.optBoolean("enable_kill_switch", false))
                .putBoolean("enable_bbr_tfo", rootObj.optBoolean("enable_bbr_tfo", true))
                .putBoolean("enable_tls_fragment", rootObj.optBoolean("enable_tls_fragment", false))
                .putBoolean("hide_hotspot_ttl", rootObj.optBoolean("hide_hotspot_ttl", true))
                .putBoolean("enable_watchdog", rootObj.optBoolean("enable_watchdog", true))
                .apply()

            val slotsArr = rootObj.optJSONArray("slots") ?: return false
            for (i in 0 until slotsArr.length()) {
                val sObj = slotsArr.getJSONObject(i)
                val slotIdx = sObj.optInt("slot", i).coerceIn(0, ProfileManager.MAX_SLOTS - 1)
                val pkgs = mutableSetOf<String>()
                val pArr = sObj.optJSONArray("selected_packages")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) pkgs.add(pArr.getString(j))
                }
                context.getSharedPreferences("nameless_slot_$slotIdx", Context.MODE_PRIVATE).edit()
                    .putString("proxy_type", sObj.optString("proxy_type", "SOCKS5"))
                    .putString("transport_mode", sObj.optString("transport_mode", "TCP_AND_UDP"))
                    .putString("ip_mode", sObj.optString("ip_mode", "IPV4_ONLY"))
                    .putString("host", sObj.optString("host", ""))
                    .putString("port", sObj.optString("port", "1080"))
                    .putString("username", sObj.optString("username", ""))
                    .putString("password", sObj.optString("password", ""))
                    .putString("sni", sObj.optString("sni", ""))
                    .putString("ss_method", sObj.optString("ss_method", "2022-blake3-aes-128-gcm"))
                    .putString("reality_pk", sObj.optString("reality_pk", ""))
                    .putString("reality_sid", sObj.optString("reality_sid", ""))
                    .putInt("relay_slot", sObj.optInt("relay_slot", -1))
                    .putBoolean("route_whole_profile", sObj.optBoolean("route_whole_profile", true))
                    .putStringSet("selected_packages", pkgs)
                    .apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
