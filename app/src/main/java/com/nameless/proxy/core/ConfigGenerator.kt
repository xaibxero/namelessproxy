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
    val realityShortId: String = ""
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

        // 2. DNS Engine
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDns = JSONObject().apply {
            put("tag", "dns-remote")
            put("type", "tcp")
            put("server", "1.1.1.1")
            put("server_port", 53)
            put("detour", "proxy-out")
        }
        dnsServers.put(remoteDns)
        dns.put("servers", dnsServers)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        root.put("dns", dns)

        // 3. Inbounds: Native TUN Inbound (Zero 4G flapping, zero DNS/WebRTC leaks)
        val inbounds = JSONArray()
        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "tun0")
            val addressArray = JSONArray().apply {
                put("172.19.0.1/28")
            }
            put("address", addressArray)
            put("auto_route", true)
            put("strict_route", true)
            put("stack", "mixed")
            put("sniff", true)
            put("sniff_override_destination", true)
        }
        inbounds.put(tunInbound)

        // Internal SOCKS for local diagnostics
        val internalSocksInbound = JSONObject().apply {
            put("type", "socks")
            put("tag", "internal-socks-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort + 1)
        }
        inbounds.put(internalSocksInbound)

        root.put("inbounds", inbounds)

        // 4. Outbounds
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
            ProxyType.SHADOWSOCKS -> {
                proxyOutbound.put("type", "shadowsocks")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("method", settings.ssMethod.ifEmpty { "2022-blake3-aes-128-gcm" })
                proxyOutbound.put("password", settings.password)
            }
            ProxyType.VLESS -> {
                proxyOutbound.put("type", "vless")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("uuid", settings.password.ifEmpty { settings.username })
                if (settings.transportMode == TransportMode.TCP_ONLY) {
                    proxyOutbound.put("flow", "xtls-rprx-vision")
                }
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                    if (settings.realityPublicKey.isNotEmpty()) {
                        val realityObj = JSONObject().apply {
                            put("enabled", true)
                            put("public_key", settings.realityPublicKey)
                            put("short_id", settings.realityShortId)
                        }
                        put("reality", realityObj)
                    }
                }
                proxyOutbound.put("tls", tlsObj)
            }
            ProxyType.TROJAN -> {
                proxyOutbound.put("type", "trojan")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                }
                proxyOutbound.put("tls", tlsObj)
            }
            ProxyType.HYSTERIA2 -> {
                proxyOutbound.put("type", "hysteria2")
                proxyOutbound.put("tag", "proxy-out")
                proxyOutbound.put("server", settings.host)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { settings.host })
                }
                proxyOutbound.put("tls", tlsObj)
            }
        }
        outbounds.put(proxyOutbound)

        val directOutbound = JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        }
        outbounds.put(directOutbound)
        root.put("outbounds", outbounds)

        // 5. Routing Rules (Native DNS hijacking & auto interface binding)
        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-remote")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        val dnsRouteRule = JSONObject().apply {
            put("action", "hijack-dns")
        }
        routeRules.put(dnsRouteRule)

        if (settings.transportMode == TransportMode.TCP_ONLY) {
            val quicRejectRule = JSONObject().apply {
                put("network", "udp")
                val portArray = JSONArray().apply { put(443); put(3478) }
                put("port", portArray)
                put("action", "reject")
            }
            routeRules.put(quicRejectRule)
        }

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
