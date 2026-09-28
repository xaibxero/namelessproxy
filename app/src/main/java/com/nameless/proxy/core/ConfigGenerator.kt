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

        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val slotId = user * ProfileManager.MAX_SLOTS + slot

        // 1. Logging
        val log = JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // 2. DNS Engine
        val dns = JSONObject()
        val dnsServers = JSONArray()

        // Local DNS: Used exclusively for connectivity probes and proxy hostname resolution
        dnsServers.put(JSONObject().apply {
            put("tag", "dns-local")
            put("type", "local")
        })

        // Remote DNS: Securely routed through proxy tunnel for all applications and browsers
        dnsServers.put(JSONObject().apply {
            put("tag", "dns-remote")
            put("type", "tcp")
            put("server", "1.1.1.1")
            put("server_port", 53)
            put("detour", "proxy-out")
        })
        dns.put("servers", dnsServers)

        // DNS Rules: Allow Android connectivity checks to resolve directly so 4G never flaps
        val dnsRules = JSONArray()
        dnsRules.put(JSONObject().apply {
            val probeDomains = JSONArray().apply {
                put("connectivitycheck.gstatic.com")
                put("clients3.google.com")
            }
            put("domain_suffix", probeDomains)
            put("server", "dns-local")
        })
        dns.put("rules", dnsRules)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        // All unhandled DNS resolves securely through the US proxy
        dns.put("final", "dns-remote")
        root.put("dns", dns)

        // 3. Inbounds: Clean TUN Inbound (Free of deprecated legacy fields and tagless-compatible)
        val inbounds = JSONArray()
        val ifaceName = "nlp${user}s${slot}"
        val v4 = "172.19.${user % 200}.${slot * 4 + 1}/30"
        val v6 = "fdfe:dcba:9876:${Integer.toHexString(slotId + 1)}::1/126"

        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", ifaceName)
            put("address", JSONArray().apply {
                put(v4)
                put(v6)
            })
            put("auto_route", true)
            put("strict_route", false)
            put("iproute2_table_index", 3000 + slotId)
            put("iproute2_rule_index", 9000 + slotId * 10)
        }
        inbounds.put(tunInbound)

        // Internal SOCKS for diagnostics and in-app latency tester
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
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", "chrome")
                        })
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

        // 5. Routing Rules (Modern sing-box 1.13+ rule actions)
        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-local")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        // Sniff rule (mandatory first rule in sing-box 1.13+)
        routeRules.put(JSONObject().apply {
            put("action", "sniff")
        })

        // DNS Hijack rule: capture all port 53 traffic into sing-box DNS engine
        routeRules.put(JSONObject().apply {
            put("protocol", "dns")
            put("action", "hijack-dns")
        })

        // Route Android connectivity probes directly
        routeRules.put(JSONObject().apply {
            val probeDomains = JSONArray().apply {
                put("connectivitycheck.gstatic.com")
                put("clients3.google.com")
            }
            put("domain_suffix", probeDomains)
            put("outbound", "direct-out")
        })

        // LAN / private networks stay direct
        routeRules.put(JSONObject().apply {
            put("ip_is_private", true)
            put("outbound", "direct-out")
        })

        // Direct outbound for proxy host to prevent routing loops
        if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
            val isIpv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(settings.host)
            if (isIpv4) {
                routeRules.put(JSONObject().apply {
                    put("ip_cidr", JSONArray().apply { put("${settings.host}/32") })
                    put("outbound", "direct-out")
                })
            } else {
                routeRules.put(JSONObject().apply {
                    put("domain", JSONArray().apply { put(settings.host) })
                    put("outbound", "direct-out")
                })
            }
        }

        // WebRTC STUN and QUIC leak mitigation
        if (settings.transportMode == TransportMode.TCP_ONLY) {
            routeRules.put(JSONObject().apply {
                put("network", "udp")
                put("port", JSONArray().apply { put(443); put(3478) })
                put("action", "reject")
            })
        }

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
