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
    val enableKillSwitch: Boolean = false
)

object ConfigGenerator {

    const val SINGBOX_ROUTING_MARK = 0x2026

    fun cleanHost(rawHost: String): String =
        rawHost.trim().removePrefix("[").removeSuffix("]")

    fun generateJson(settings: ProxySettings, inboundPort: Int): String {
        val root = JSONObject()
        val serverHost = cleanHost(settings.host)

        // 1. Logging
        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        // 2. DNS Engine (Supports IPv4, Dual-Stack, and IPv6-Only)
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) "2606:4700:4700::1111" else "1.1.1.1"
        val directDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) "2001:4860:4860::8888" else "8.8.8.8"

        dnsServers.put(JSONObject().apply {
            put("tag", "dns-remote")
            put("type", "tcp")
            put("server", remoteDnsServer)
            put("server_port", 53)
            put("detour", "proxy-out")
        })

        dnsServers.put(JSONObject().apply {
            put("tag", "dns-direct")
            put("type", "udp")
            put("server", directDnsServer)
            put("server_port", 53)
        })
        dns.put("servers", dnsServers)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        dns.put("independent_cache", true)
        root.put("dns", dns)

        // 3. Inbounds (Dual-stack "::" listens on both IPv4 and IPv6)
        val inbounds = JSONArray()

        inbounds.put(JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", "::")
            put("listen_port", inboundPort)
        })

        inbounds.put(JSONObject().apply {
            put("type", "socks")
            put("tag", "internal-socks-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort + 1)
        })

        inbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "dns-in")
            put("listen", "::")
            put("listen_port", inboundPort + 2)
            put("network", JSONArray().apply {
                put("tcp")
                put("udp")
            })
            put("override_address", remoteDnsServer)
            put("override_port", 53)
        })

        inbounds.put(JSONObject().apply {
            put("type", "tproxy")
            put("tag", "tproxy-in")
            put("listen", "::")
            put("listen_port", inboundPort + 8)
            put("network", JSONArray().apply {
                put("tcp")
                put("udp")
            })
        })

        root.put("inbounds", inbounds)

        // 4. Outbounds
        val outbounds = JSONArray()
        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy-out")
            put("domain_resolver", "dns-direct")
            put("routing_mark", SINGBOX_ROUTING_MARK)
        }

        when (settings.type) {
            ProxyType.SOCKS5 -> {
                proxyOutbound.put("type", "socks")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("version", "5")
                if (settings.username.isNotEmpty()) {
                    proxyOutbound.put("username", settings.username)
                    proxyOutbound.put("password", settings.password)
                }
            }
            ProxyType.SOCKS4 -> {
                proxyOutbound.put("type", "socks")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("version", "4")
            }
            ProxyType.HTTP -> {
                proxyOutbound.put("type", "http")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                if (settings.username.isNotEmpty()) {
                    proxyOutbound.put("username", settings.username)
                    proxyOutbound.put("password", settings.password)
                }
            }
            ProxyType.SHADOWSOCKS -> {
                proxyOutbound.put("type", "shadowsocks")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("method", settings.ssMethod.ifEmpty { "2022-blake3-aes-128-gcm" })
                proxyOutbound.put("password", settings.password)
            }
            ProxyType.VLESS -> {
                proxyOutbound.put("type", "vless")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("uuid", settings.password.ifEmpty { settings.username })
                if (settings.transportMode == TransportMode.TCP_ONLY || settings.realityPublicKey.isNotEmpty()) {
                    proxyOutbound.put("flow", "xtls-rprx-vision")
                }
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { serverHost })
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
                proxyOutbound.put("tls", tlsObj)
            }
            ProxyType.TROJAN -> {
                proxyOutbound.put("type", "trojan")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password)
                proxyOutbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { serverHost })
                })
            }
            ProxyType.HYSTERIA2 -> {
                proxyOutbound.put("type", "hysteria2")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password)
                proxyOutbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { serverHost })
                })
            }
        }
        outbounds.put(proxyOutbound)

        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
            put("routing_mark", SINGBOX_ROUTING_MARK)
        })
        root.put("outbounds", outbounds)

        // 5. Routing Rules
        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-direct")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        routeRules.put(JSONObject().apply {
            put("inbound", JSONArray().apply { put("dns-in") })
            put("action", "hijack-dns")
        })
        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(53) })
            put("action", "hijack-dns")
        })

        val rejectQuicUdp = settings.transportMode == TransportMode.TCP_ONLY ||
            settings.type == ProxyType.SOCKS5 ||
            settings.type == ProxyType.SOCKS4 ||
            settings.type == ProxyType.HTTP

        routeRules.put(JSONObject().apply {
            put("network", "udp")
            put("port", JSONArray().apply {
                put(3478)
                put(5349)
                put(19302)
                put(853)
                if (rejectQuicUdp) {
                    put(80)
                    put(443)
                }
            })
            put("action", "reject")
        })

        routeRules.put(JSONObject().apply {
            put("action", "sniff")
            put("timeout", "300ms")
        })

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
