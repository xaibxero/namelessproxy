package com.nameless.proxy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nameless.proxy.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val globalPrefs = context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        val startOnBoot = globalPrefs.getBoolean("start_on_boot", false)
        if (!startOnBoot) return

        val bootSlot = globalPrefs.getInt("boot_slot", 0)
        val routeHotspot = globalPrefs.getBoolean("route_hotspot", true)
        val slotPrefs = context.getSharedPreferences("nameless_slot_$bootSlot", Context.MODE_PRIVATE)

        // Keep receiver process alive during async root + daemon startup
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = ProfileManager.androidUserId

                // If service.d boot script is installed, give it priority so we don't collide with it
                if (BootManager.isBootScriptInstalled()) {
                    delay(6500)
                }

                // If already started by the kernel service.d script, exit cleanly
                if (ProxyController.isRunning(user, bootSlot)) return@launch

                var host = slotPrefs.getString("host", "")?.trim() ?: ""
                var port = slotPrefs.getString("port", "1080")?.toIntOrNull() ?: 1080
                var username = slotPrefs.getString("username", "") ?: ""
                var password = slotPrefs.getString("password", "") ?: ""
                var sni = slotPrefs.getString("sni", "") ?: ""
                var ssMethod = slotPrefs.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm"
                var rpk = slotPrefs.getString("reality_pk", "") ?: ""
                var rsid = slotPrefs.getString("reality_sid", "") ?: ""
                var type = try { ProxyType.valueOf(slotPrefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (_: Exception) { ProxyType.SOCKS5 }
                var transportMode = try { TransportMode.valueOf(slotPrefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (_: Exception) { TransportMode.TCP_AND_UDP }
                var ipMode = try { IpMode.valueOf(slotPrefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (_: Exception) { IpMode.IPV4_ONLY }
                var routeWholeProfile = slotPrefs.getBoolean("route_whole_profile", true)
                var selectedPackages = slotPrefs.getStringSet("selected_packages", emptySet()) ?: emptySet()

                // Fallback to /data/adb/nameless_proxy backup if SharedPreferences was cleared
                if (host.isEmpty()) {
                    val backup = PersistentStorage.loadBackup(bootSlot, user)
                    if (backup != null) {
                        host = backup.optString("host", "").trim()
                        port = backup.optString("port", "1080").toIntOrNull() ?: 1080
                        username = backup.optString("username", "")
                        password = backup.optString("password", "")
                        sni = backup.optString("sni", "")
                        ssMethod = backup.optString("ss_method", "2022-blake3-aes-128-gcm")
                        rpk = backup.optString("reality_pk", "")
                        rsid = backup.optString("reality_sid", "")
                        type = try { ProxyType.valueOf(backup.optString("proxy_type", ProxyType.SOCKS5.name)) } catch (_: Exception) { ProxyType.SOCKS5 }
                        transportMode = try { TransportMode.valueOf(backup.optString("transport_mode", TransportMode.TCP_AND_UDP.name)) } catch (_: Exception) { TransportMode.TCP_AND_UDP }
                        ipMode = try { IpMode.valueOf(backup.optString("ip_mode", IpMode.IPV4_ONLY.name)) } catch (_: Exception) { IpMode.IPV4_ONLY }
                        routeWholeProfile = backup.optBoolean("route_whole_profile", true)

                        val pkgSet = mutableSetOf<String>()
                        val arr = backup.optJSONArray("selected_packages")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                pkgSet.add(arr.getString(i))
                            }
                        }
                        selectedPackages = pkgSet
                    }
                }

                if (host.isEmpty()) return@launch

                val settings = ProxySettings(
                    type = type,
                    transportMode = transportMode,
                    ipMode = ipMode,
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    routeHotspot = routeHotspot,
                    sni = sni,
                    ssMethod = ssMethod,
                    realityPublicKey = rpk,
                    realityShortId = rsid
                )

                val uids = if (routeWholeProfile) null else {
                    val pm = context.packageManager
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { selectedPackages.contains(it.packageName) }
                        .map { it.uid }
                }

                ProfileManager.activeSlot = bootSlot
                ProxyController.startProxy(context, settings, uids)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
