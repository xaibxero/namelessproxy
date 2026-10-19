package com.nameless.proxy.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nameless.proxy.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NamelessTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Syncing..."
        }
        tile.updateTile()

        serviceScope.launch {
            val user = ProfileManager.androidUserId
            val runningSlot = ProxyController.getRunningSlot(user)

            if (runningSlot != null) {
                ProxyController.stopProxy(applicationContext, user)
            } else {
                val slot = ProfileManager.activeSlot
                val globalPrefs = applicationContext.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
                val routeHotspot = globalPrefs.getBoolean("route_hotspot", true)
                val slotPrefs = applicationContext.getSharedPreferences("nameless_slot_$slot", Context.MODE_PRIVATE)
                val host = slotPrefs.getString("host", "")?.trim() ?: ""

                if (host.isNotEmpty()) {
                    val settings = ProxySettings(
                        type = try { ProxyType.valueOf(slotPrefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (_: Exception) { ProxyType.SOCKS5 },
                        transportMode = try { TransportMode.valueOf(slotPrefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (_: Exception) { TransportMode.TCP_AND_UDP },
                        ipMode = try { IpMode.valueOf(slotPrefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (_: Exception) { IpMode.IPV4_ONLY },
                        host = host,
                        port = slotPrefs.getString("port", "1080")?.toIntOrNull() ?: 1080,
                        username = slotPrefs.getString("username", "") ?: "",
                        password = slotPrefs.getString("password", "") ?: "",
                        routeHotspot = routeHotspot,
                        sni = slotPrefs.getString("sni", "") ?: "",
                        ssMethod = slotPrefs.getString("ss_method", "chacha20-ietf-poly1305") ?: "chacha20-ietf-poly1305",
                        realityPublicKey = slotPrefs.getString("reality_pk", "") ?: "",
                        realityShortId = slotPrefs.getString("reality_sid", "") ?: ""
                    )

                    val routeWhole = slotPrefs.getBoolean("route_whole_profile", true)
                    val selectedPkgs = slotPrefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
                    val uids = if (routeWhole) null else {
                        val targets = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                            .filter { selectedPkgs.contains(it.packageName) }
                            .map { it.uid }
                            .toMutableSet()
                        targets.add(applicationInfo.uid)
                        targets.toList()
                    }
                    ProxyController.startProxy(applicationContext, settings, uids)
                }
            }

            refreshTileState()
        }
    }

    private fun refreshTileState() {
        serviceScope.launch {
            val user = ProfileManager.androidUserId
            val runningSlot = ProxyController.getRunningSlot(user)
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                if (runningSlot != null) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Nameless • P$runningSlot"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Tunnel Active"
                    }
                } else {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Nameless Proxy"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Standby (P${ProfileManager.activeSlot})"
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
