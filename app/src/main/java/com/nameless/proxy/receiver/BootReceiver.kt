package com.nameless.proxy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.nameless.proxy.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val globalPrefs = context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        val startOnBoot = globalPrefs.getBoolean("start_on_boot", false)
        if (!startOnBoot) return

        val bootSlot = globalPrefs.getInt("boot_slot", 0)
        val routeHotspot = globalPrefs.getBoolean("route_hotspot", true)
        val slotPrefs = context.getSharedPreferences("nameless_slot_$bootSlot", Context.MODE_PRIVATE)
        val host = slotPrefs.getString("host", "") ?: ""

        if (host.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val user = ProfileManager.androidUserId
            // If already started by the kernel service.d script, don't duplicate
            if (ProxyController.isRunning(user, bootSlot)) return@launch

            val settings = ProxySettings(
                type = try { ProxyType.valueOf(slotPrefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (e: Exception) { ProxyType.SOCKS5 },
                transportMode = try { TransportMode.valueOf(slotPrefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (e: Exception) { TransportMode.TCP_AND_UDP },
                ipMode = try { IpMode.valueOf(slotPrefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (e: Exception) { IpMode.IPV4_ONLY },
                host = host.trim(),
                port = slotPrefs.getString("port", "1080")?.toIntOrNull() ?: 1080,
                username = slotPrefs.getString("username", "") ?: "",
                password = slotPrefs.getString("password", "") ?: "",
                routeHotspot = routeHotspot,
                sni = slotPrefs.getString("sni", "") ?: "",
                ssMethod = slotPrefs.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm",
                realityPublicKey = slotPrefs.getString("reality_pk", "") ?: "",
                realityShortId = slotPrefs.getString("reality_sid", "") ?: ""
            )

            val routeWholeProfile = slotPrefs.getBoolean("route_whole_profile", true)
            val selectedPackages = slotPrefs.getStringSet("selected_packages", emptySet()) ?: emptySet()

            val uids = if (routeWholeProfile) null else {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { selectedPackages.contains(it.packageName) }
                    .map { it.uid }
            }

            ProfileManager.activeSlot = bootSlot
            ProxyController.startProxy(context, settings, uids)
        }
    }
}
