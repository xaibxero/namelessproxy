package com.nameless.proxy.core

import org.json.JSONArray
import org.json.JSONObject

enum class ProxyType {
    SOCKS5, SOCKS4, HTTP
}

enum class TransportMode {
    TCP_AND_UDP, // Full transparent proxying (WebRTC enabled through proxy)
    TCP_ONLY     // TCP only
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
    val host: String = "48.45.153.215",
    val port: Int = 46508,
    val username: String = "FgCH4MnS3EQDohq",
    val password: String = "SzrAO5ADxzz81RP"
)

object ConfigGenerator {
    fun generateJson(settings: ProxySettings, inboundPort: Int): String {
        val root = JSONObject()

        // 1. Logging
        val log = JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // 2. Inbound bind address
        val listenAddress = when (settings.ipMode) {
            IpMode.IPV4_ONLY -> "127.0.0.1"
            IpMode.DUAL_STACK -> "::"
            IpMode.IPV6_ONLY -> "::1"
        }

        val inbounds = JSONArray()

        // TCP Inbound (Kernel REDIRECT)
        val redirectInbound = JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
        }
        inbounds.put(redirectInbound)

        // UDP Inbound (Kernel TPROXY for WebRTC, STUN, and UDP streams)
        if (settings.transportMode == TransportMode.TCP_AND_UDP && settings.type == ProxyType.SOCKS5) {
            val tproxyInbound = JSONObject().apply {
                put("type", "tproxy")
                put("tag", "tproxy-in")
                put("listen", listenAddress)
                put("listen_port", inboundPort)
                put("network", "udp")
            }
            inbounds.put(tproxyInbound)
        }

        root.put("inbounds", inbounds)

        // 3. Outbounds
        val outbounds = JSONArray()
        val proxyOutbound = JSONObject()

        when (settings.type) {
            ProxyType.SOCKS5 -> {
                proxyOutbound.put("type", "socks")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("version", "5")
                if (settings.username.isNotEmpty()) {
                    proxyOutbound.put("username", settings.username)
                    proxyOutbound.put("password", settings.password)
                }
            }
            ProxyType.SOCKS4 -> {
                proxyOutbound.put("type", "socks")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("version", "4")
            }
            ProxyType.HTTP -> {
                proxyOutbound.put("type", "http")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                if (settings.username.isNotEmpty()) {
                    proxyOutbound.put("username", settings.username)
                    proxyOutbound.put("password", settings.password)
                }
            }
        }
        outbounds.put(proxyOutbound)

        val directOutbound = JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        }
        outbounds.put(directOutbound)
        root.put("outbounds", outbounds)

        // 4. Default Routing
        val route = JSONObject().apply {
            put("final", "proxy-out")
        }
        root.put("route", route)

        return root.toString(2)
    }
}
