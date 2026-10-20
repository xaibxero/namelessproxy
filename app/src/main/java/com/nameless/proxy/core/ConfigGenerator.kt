package com.nameless.proxy.core

import org.json.JSONArray
import org.json.JSONObject

enum class ProxyType {
    SOCKS5, HTTP, SHADOWSOCKS, VLESS, VMESS, TROJAN, HYSTERIA2, TUIC, SOCKS4
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
    val ssMethod: String = "chacha20-ietf-poly1305",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val strictWebRtcShield: Boolean = true
)

object ConfigGenerator {

    const val BOOTSTRAP_DNS_IPV4 = "9.9.9.9"
    const val BOOTSTRAP_DNS_IPV6 = "2620:fe::fe"

    fun cleanHost(rawHost: String): String =
        rawHost.trim().removePrefix("[").removeSuffix("]")

    fun resolveAutoIpMode(settings: ProxySettings, resolvedServerHost: String? = null): IpMode {
        val candidate = resolvedServerHost?.takeIf { it.isNotBlank() } ?: cleanHost(settings.host)
        return if (IptablesManager.isValidIpv6(candidate)) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
    }

    fun generateJson(
        settings: ProxySettings,
        inboundPort: Int,
        resolvedServerHost: String? = null
    ): String {
        val root = JSONObject()
        val rawCleanedHost = cleanHost(settings.host)
        val serverHost = resolvedServerHost?.takeIf { it.isNotBlank() } ?: rawCleanedHost
        val effectiveIpMode = resolveAutoIpMode(settings, serverHost)
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot

        // 1. Logging
        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        // 2. Experimental Cache File for FakeIP persistence
        root.put("experimental", JSONObject().apply {
            put("cache_file", JSONObject().apply {
                put("enabled", true)
                put("path", "/data/adb/nameless_proxy/cache_u${user}_s${slot}.db")
                put("store_fakeip", true)
            })
        })

        // 3. DNS Configuration (FakeIP + Remote TCP Tunnel through Proxy)
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDnsServer = if (effectiveIpMode == IpMode.IPV6_ONLY) "2606:4700:4700::1111" else "1.1.1.1"
        val directDnsServer = if (effectiveIpMode == IpMode.IPV6_ONLY) BOOTSTRAP_DNS_IPV6 else BOOTSTRAP_DNS_IPV4

        dnsServers.put(JSONObject().apply {
            put("type", "fakeip")
            put("tag", "dns-fakeip")
            put("inet4_range", "198.18.0.0/15")
            put("inet6_range", "fc00::/18")
        })

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

        val dnsRules = JSONArray()

        // Return clean NOERROR (0 answers) for HTTPS (65), SVCB (64), and AAAA (when IPv4_ONLY)
        // so Chromium's parallel HostResolverDnsTask never aborts on RCODE=REFUSED!
        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                put("HTTPS")
                put("SVCB")
                if (effectiveIpMode == IpMode.IPV4_ONLY) {
                    put("AAAA")
                }
                if (effectiveIpMode == IpMode.IPV6_ONLY) {
                    put("A")
                }
            })
            put("action", "predefined")
            put("rcode", "NOERROR")
        })

        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                if (effectiveIpMode != IpMode.IPV6_ONLY) put("A")
                if (effectiveIpMode != IpMode.IPV4_ONLY) put("AAAA")
            })
            put("action", "route")
            put("server", "dns-fakeip")
            put("rewrite_ttl", 20)
        })
        dns.put("rules", dnsRules)

        when (effectiveIpMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        dns.put("reverse_mapping", true)
        root.put("dns", dns)

        // 4. Inbounds — Dedicated AF_INET (127.0.0.1 / 0.0.0.0) sockets so disable_ipv6=1 on rmnet_data0
        // never blocks TCP/UDP socket delivery on the Phone or Hotspot!
        val inbounds = JSONArray()

        // Phone OUTPUT TCP REDIRECT inbound (bound explicitly to 127.0.0.1 AF_INET)
        inbounds.put(JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort)
        })

        // Local Mixed (SOCKS5 + HTTP/HTTPS CONNECT) proxy inbound for IpFetcher, StunResponder & WebView
        inbounds.put(JSONObject().apply {
            put("type", "mixed")
            put("tag", "internal-socks-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort + 1)
        })

        // Phone OUTPUT DNS REDIRECT inbound (bound explicitly to 127.0.0.1 AF_INET)
        inbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "dns-in")
            put("listen", "127.0.0.1")
            put("listen_port", inboundPort + 2)
            put("network", JSONArray().apply {
                put("tcp")
                put("udp")
            })
            put("override_address", "1.1.1.1")
            put("override_port", 53)
        })

        // Hotspot PREROUTING DNS REDIRECT inbound (bound explicitly to 0.0.0.0 AF_INET)
        inbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "dns-hs-in")
            put("listen", "0.0.0.0")
            put("listen_port", inboundPort + 4)
            put("network", JSONArray().apply {
                put("tcp")
                put("udp")
            })
            put("override_address", "1.1.1.1")
            put("override_port", 53)
        })

        // Hotspot PREROUTING TCP REDIRECT inbound (bound explicitly to 0.0.0.0 AF_INET)
        inbounds.put(JSONObject().apply {
            put("type", "redirect")
            put("tag", "redirect-hs-in")
            put("listen", "0.0.0.0")
            put("listen_port", inboundPort + 6)
        })

        root.put("inbounds", inbounds)

        // 5. Outbounds (Never set routing_mark on Android so fwmark 0x0/0xffff matches netd Rule 22000!)
        val outbounds = JSONArray()
        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy-out")
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
                if (settings.sni.isNotEmpty()) {
                    proxyOutbound.put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", settings.sni)
                    })
                }
            }
            ProxyType.SHADOWSOCKS -> {
                proxyOutbound.put("type", "shadowsocks")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("method", settings.ssMethod.ifEmpty { "chacha20-ietf-poly1305" })
                proxyOutbound.put("password", settings.password)
            }
            ProxyType.VLESS -> {
                proxyOutbound.put("type", "vless")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("uuid", settings.password.ifEmpty { settings.username })
                proxyOutbound.put("packet_encoding", "xudp")
                if (settings.realityPublicKey.isNotEmpty()) {
                    proxyOutbound.put("flow", "xtls-rprx-vision")
                }
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", "chrome")
                    })
                    if (settings.realityPublicKey.isNotEmpty()) {
                        put("reality", JSONObject().apply {
                            put("enabled", true)
                            put("public_key", settings.realityPublicKey)
                            put("short_id", settings.realityShortId)
                        })
                    }
                }
                proxyOutbound.put("tls", tlsObj)
            }
            ProxyType.VMESS -> {
                proxyOutbound.put("type", "vmess")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("uuid", settings.password.ifEmpty { settings.username })
                proxyOutbound.put("security", "auto")
                proxyOutbound.put("alter_id", 0)
                if (settings.sni.isNotEmpty() || settings.port == 443) {
                    proxyOutbound.put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                    })
                }
            }
            ProxyType.TROJAN -> {
                proxyOutbound.put("type", "trojan")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password.ifEmpty { settings.username })
                proxyOutbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", "chrome")
                    })
                })
            }
            ProxyType.HYSTERIA2 -> {
                proxyOutbound.put("type", "hysteria2")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password.ifEmpty { settings.username })
                proxyOutbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                })
            }
            ProxyType.TUIC -> {
                proxyOutbound.put("type", "tuic")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("uuid", settings.username.ifEmpty { settings.password })
                proxyOutbound.put("password", settings.password)
                proxyOutbound.put("congestion_control", "bbr")
                proxyOutbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                })
            }
        }
        outbounds.put(proxyOutbound)

        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        })
        root.put("outbounds", outbounds)

        // 6. Routing Rules
        val route = JSONObject().apply {
            put("auto_detect_interface", false)
            put("default_domain_resolver", "dns-direct")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        routeRules.put(JSONObject().apply {
            put("inbound", JSONArray().apply {
                put("dns-in")
                put("dns-hs-in")
            })
            put("action", "hijack-dns")
        })
        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(53) })
            put("action", "hijack-dns")
        })

        routeRules.put(JSONObject().apply {
            put("action", "sniff")
            put("timeout", "200ms")
        })

        // Block UDP QUIC/DoQ on ports 80/443/853 so browsers & Private DNS use TCP 443/853 through proxy-out
        routeRules.put(JSONObject().apply {
            put("network", "udp")
            put("port", JSONArray().apply {
                put(80)
                put(443)
                put(853)
            })
            put("action", "reject")
        })

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
