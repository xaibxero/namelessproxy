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

        val directDns = JSONObject().apply {
            put("tag", "dns-direct")
            put("type", "udp")
            put("server", "1.1.1.1")
            put("server_port", 53)
        }
        dnsServers.put(directDns)
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
            IpMode.DUAL_STACK -> "::"
            IpMode.IPV6_ONLY -> "::"
        }

        val inbounds = JSONArray()

        val redirectInbound = JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
        }
        inbounds.put(redirectInbound)

        val tproxyInbound = JSONObject().apply {
            put("type", "tproxy")
            put("tag", "tproxy-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
            put("network", "udp")
        }
        inbounds.put(tproxyInbound)

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

        // 5. Routing Rules
        val route = JSONObject().apply {
            put("default_domain_resolver", "dns-remote")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        val sniffRule = JSONObject().apply {
            put("action", "sniff")
        }
        routeRules.put(sniffRule)

        val dnsRouteRule = JSONObject().apply {
            val portArray = JSONArray().apply { put(53) }
            put("port", portArray)
            put("action", "hijack-dns")
        }
        routeRules.put(dnsRouteRule)

        if (settings.transportMode == TransportMode.TCP_ONLY) {
            val quicFallbackRule = JSONObject().apply {
                put("network", "udp")
                val portArray = JSONArray().apply { put(443) }
                put("port", portArray)
                put("action", "reject")
            }
            routeRules.put(quicFallbackRule)
        }

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
