package com.nameless.proxy.core

import org.json.JSONArray
import org.json.JSONObject

enum class ProxyType {
    SOCKS5, SOCKS4, HTTP
}

enum class IpMode {
    IPV4_ONLY,
    DUAL_STACK,
    IPV6_ONLY
}

data class ProxySettings(
    val type: ProxyType = ProxyType.SOCKS5,
    val ipMode: IpMode = IpMode.IPV4_ONLY,
    val host: String = "127.0.0.1",
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
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

        // 2. Inbound
        val listenAddress = when (settings.ipMode) {
            IpMode.IPV4_ONLY -> "127.0.0.1"
            IpMode.DUAL_STACK -> "::"
            IpMode.IPV6_ONLY -> "::1"
        }

        val inbounds = JSONArray()
        val redirectInbound = JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
        }
        inbounds.put(redirectInbound)
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

        // Direct outbound
        val directOutbound = JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        }
        outbounds.put(directOutbound)
        root.put("outbounds", outbounds)

        // 4. Route block
        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            put("final", "proxy-out")
        }
        root.put("route", route)

        return root.toString(2)
    }
}
