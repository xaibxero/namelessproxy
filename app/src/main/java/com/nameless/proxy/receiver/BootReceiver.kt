package com.nameless.proxy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nameless.proxy.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("nameless_proxy_config", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)

            if (startOnBoot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val settings = ProxySettings(
                        type = try {
                            ProxyType.valueOf(prefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name)
                        } catch (_: Exception) { ProxyType.SOCKS5 },
                        transportMode = try {
                            TransportMode.valueOf(prefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name)
                        } catch (_: Exception) { TransportMode.TCP_AND_UDP },
                        ipMode = try {
                            IpMode.valueOf(prefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name)
                        } catch (_: Exception) { IpMode.IPV4_ONLY },
                        host = prefs.getString("host", "") ?: "",
                        port = (prefs.getString("port", "1080") ?: "1080").toIntOrNull() ?: 1080,
                        username = prefs.getString("username", "") ?: "",
                        password = prefs.getString("password", "") ?: ""
                    )

                    if (settings.host.isNotEmpty()) {
                        ProxyController.startProxy(context, settings, null)
                    }
                }
            }
        }
    }
}
