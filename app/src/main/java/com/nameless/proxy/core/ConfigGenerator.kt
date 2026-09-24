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
    val host: String = "",
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

        // 2. DNS Engine (Resolves queries through remote proxy tunnel)
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDns = JSONObject().apply {
            put("tag", "dns-remote")
            put("address", "tcp://8.8.8.8")
            put("detour", "proxy-out")
        }
        dnsServers.put(remoteDns)

        val directDns = JSONObject().apply {
            put("tag", "dns-direct")
            put("address", "1.1.1.1")
            put("detour", "direct-out")
        }
        dnsServers.put(directDns)
        dns.put("servers", dnsServers)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        val dnsRules = JSONArray()
        val directDnsRule = JSONObject().apply {
            put("outbound", "direct-out")
            put("server", "dns-direct")
        }
        dnsRules.put(directDnsRule)
        dns.put("rules", dnsRules)
        dns.put("final", "dns-remote")

        root.put("dns", dns)

        // 3. Inbounds
        val listenAddress = when (settings.ipMode) {
            IpMode.IPV4_ONLY -> "127.0.0.1"
            IpMode.DUAL_STACK -> "::"
            IpMode.IPV6_ONLY -> "::1"
        }

        val inbounds = JSONArray()

        // TCP Inbound
        val redirectInbound = JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", listenAddress)
            put("listen_port", inboundPort)
        }
        inbounds.put(redirectInbound)

        // UDP Inbound (WebRTC & UDP DNS Interception)
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

        // 4. Outbounds
        val outbounds = JSONArray()

        // Internal DNS Handler Outbound
        val dnsOutbound = JSONObject().apply {
            put("type", "dns")
            put("tag", "dns-out")
        }
        outbounds.put(dnsOutbound)

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

        // 5. Routing Rules
        val route = JSONObject()
        val routeRules = JSONArray()

        // Divert port 53 traffic into internal DNS engine
        val dnsRouteRule = JSONObject().apply {
            val portArray = JSONArray().apply { put(53) }
            put("port", portArray)
            put("outbound", "dns-out")
        }
        routeRules.put(dnsRouteRule)

        route.put("rules", routeRules)
        route.put("final", "proxy-out")
        root.put("route", route)

        return root.toString(2)
    }
}
