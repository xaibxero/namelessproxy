package com.nameless.proxy.core

import org.json.JSONArray
import org.json.JSONObject

enum class ProxyType {
    SOCKS5, SOCKS4, HTTP, SHADOWSOCKS, VLESS, TROJAN, HYSTERIA2
}

enum class TransportMode {
    TCP_AND_UDP,
    TCP_ONLY
}

enum class IpMode {
    IPV4_ONLY,
    DUAL_STACK,
    IPV6_ONLY
}

data class ProxySettings(
    val type: ProxyType = ProxyType.SOCKS5,
    val transportMode: TransportMode = TransportMode.TCP_AND_UDP,
    val ipMode: IpMode = IpMode.IPV4_ONLY,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val routeHotspot: Boolean = true,
    val sni: String = "",
    val ssMethod: String = "2022-blake3-aes-128-gcm",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val enableKillSwitch: Boolean = false,
    val enableBbrAndTfo: Boolean = true,
    val enableTlsFragment: Boolean = false,
    val hideHotspotTtl: Boolean = true,
    val enableWatchdog: Boolean = true,
    val relaySlot: Int = -1
)

object ConfigGenerator {

    private fun buildOutboundObject(
        tag: String,
        settings: ProxySettings,
        detourTag: String? = null
    ): JSONObject {
        val obj = JSONObject().apply {
            put("tag", tag)
        }

        when (settings.type) {
            ProxyType.SOCKS5 -> {
                obj.put("type", "socks")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("version", "5")
                if (settings.username.isNotEmpty()) {
                    obj.put("username", settings.username)
                    obj.put("password", settings.password)
                }
            }
            ProxyType.SOCKS4 -> {
                obj.put("type", "socks")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("version", "4")
            }
            ProxyType.HTTP -> {
                obj.put("type", "http")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                if (settings.username.isNotEmpty()) {
                    obj.put("username", settings.username)
                    obj.put("password", settings.password)
                }
            }
            ProxyType.SHADOWSOCKS -> {
                obj.put("type", "shadowsocks")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("method", settings.ssMethod.ifEmpty { "2022-blake3-aes-128-gcm" })
                obj.put("password", settings.password)
            }
            ProxyType.VLESS -> {
                obj.put("type", "vless")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("uuid", settings.password.ifEmpty { settings.username })
                if (settings.transportMode == TransportMode.TCP_ONLY || settings.realityPublicKey.isNotEmpty()) {
                    obj.put("flow", "xtls-rprx-vision")
                }
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                    if (settings.realityPublicKey.isNotEmpty()) {
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", "chrome")
                        })
                        put("reality", JSONObject().apply {
                            put("enabled", true)
                            put("public_key", settings.realityPublicKey)
                            put("short_id", settings.realityShortId)
                        })
                    }
                }
                obj.put("tls", tlsObj)
            }
            ProxyType.TROJAN -> {
                obj.put("type", "trojan")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("password", settings.password)
                obj.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                })
            }
            ProxyType.HYSTERIA2 -> {
                obj.put("type", "hysteria2")
                obj.put("server", settings.host)
                obj.put("server_port", settings.port)
                obj.put("password", settings.password)
                obj.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                })
            }
        }

        // In sing-box, dial fields (detour vs tcp_fast_open) are mutually exclusive on the same outbound
        if (!detourTag.isNullOrEmpty()) {
            obj.put("detour", detourTag)
        } else if (settings.enableBbrAndTfo && settings.type != ProxyType.HYSTERIA2) {
            obj.put("tcp_fast_open", true)
        }

        return obj
    }

    fun generateJson(
        settings: ProxySettings,
        inboundPort: Int,
        relaySettings: ProxySettings? = null
    ): String {
        val root = JSONObject()

        // 1. Logging
        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        // 2. DNS Engine
        val dns = JSONObject()
        val dnsServers = JSONArray()

        dnsServers.put(JSONObject().apply {
            put("tag", "dns-remote")
            put("type", "tcp")
            put("server", "1.1.1.1")
            put("server_port", 53)
            put("detour", "proxy-out")
        })

        dnsServers.put(JSONObject().apply {
            put("tag", "dns-direct")
            put("type", "udp")
            put("server", "8.8.8.8")
            put("server_port", 53)
        })
        dns.put("servers", dnsServers)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        root.put("dns", dns)

        // 3. Inbounds
        val listenAddress = when (settings.ipMode) {
            IpMode.IPV4_ONLY -> "0.0.0.0"
            IpMode.DUAL_STACK, IpMode.IPV6_ONLY -> "::"
        }

        val inbounds = JSONArray()

        inbounds.put(JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
        })

        inbounds.put(JSONObject().apply {
            put("type", "tproxy")
            put("tag", "tproxy-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort + 8)
            put("network", JSONArray().apply {
                put("tcp")
                put("udp")
            })
        })

        inbounds.put(JSONObject().apply {
            put("type", "socks")
            put("tag", "internal-socks-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort + 1)
        })

        root.put("inbounds", inbounds)

        // 4. Outbounds (Supports Multi-Hop Proxy Chaining via relaySettings)
        val outbounds = JSONArray()
        val hasValidRelay = relaySettings != null &&
            relaySettings.host.isNotBlank() &&
            (relaySettings.host != settings.host || relaySettings.port != settings.port)

        if (hasValidRelay && relaySettings != null) {
            val proxyOut = buildOutboundObject("proxy-out", settings, detourTag = "relay-out")
            val relayOut = buildOutboundObject("relay-out", relaySettings, detourTag = null)
            outbounds.put(proxyOut)
            outbounds.put(relayOut)
        } else {
            val proxyOut = buildOutboundObject("proxy-out", settings, detourTag = null)
            outbounds.put(proxyOut)
        }

        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        })
        root.put("outbounds", outbounds)

        // 5. Routing Rules (Includes DPI TLS Fragmentation when enabled)
        val route = JSONObject().apply {
            put("auto_detect_interface", false)
            put("default_domain_resolver", "dns-remote")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        routeRules.put(JSONObject().apply {
            put("action", "sniff")
        })

        if (settings.enableTlsFragment) {
            routeRules.put(JSONObject().apply {
                put("action", "route-options")
                put("tls_fragment", true)
                put("tls_record_fragment", true)
            })
        }

        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(53) })
            put("action", "hijack-dns")
        })

        if (settings.transportMode == TransportMode.TCP_ONLY) {
            routeRules.put(JSONObject().apply {
                put("network", "udp")
                put("port", JSONArray().apply { put(443); put(853); put(3478) })
                put("action", "reject")
            })
        }

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
