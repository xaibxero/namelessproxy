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

    const val BOOTSTRAP_DNS_IPV4 = "9.9.9.9"
    const val BOOTSTRAP_DNS_IPV6 = "2620:fe::fe"

    private val IPV4_SIMPLE_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    fun cleanHost(rawHost: String): String =
        rawHost.trim().removePrefix("[").removeSuffix("]")

    fun generateJson(settings: ProxySettings, inboundPort: Int): String {
        val root = JSONObject()
        val serverHost = cleanHost(settings.host)
        val hostIsIpv4 = IPV4_SIMPLE_REGEX.matches(serverHost)

        // 1. Logging
        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        // 2. Official sing-box 1.14 FakeIP + Remote Tunnel DNS Engine
        // Why FakeIP? Because when sing-box returns a FakeIP (198.18.x.x) to Chrome/WebView,
        // sing-box sends the DOMAIN NAME (not an IP) through the SOCKS5/HTTP/VLESS/Trojan/Hy2 proxy!
        // That forces the remote proxy server itself to resolve the domain using its own local ISP DNS!
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) "2001:4860:4860::8888" else "8.8.8.8"
        val directDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) BOOTSTRAP_DNS_IPV6 else BOOTSTRAP_DNS_IPV4

        // FakeIP server (sing-box 1.12 - 1.14+ typed format)
        dnsServers.put(JSONObject().apply {
            put("type", "fakeip")
            put("tag", "dns-fakeip")
            put("inet4_range", "198.18.0.0/15")
            if (settings.ipMode != IpMode.IPV4_ONLY) {
                put("inet6_range", "fc00::/18")
            }
        })

        // Remote TCP DNS (fallback for non-A/AAAA queries like TXT/SRV/HTTPS)
        dnsServers.put(JSONObject().apply {
            put("tag", "dns-remote")
            put("type", "tcp")
            put("server", remoteDnsServer)
            put("server_port", 53)
            put("detour", "proxy-out")
        })

        // Direct bootstrap DNS (only used if the proxy server host itself is a domain name)
        dnsServers.put(JSONObject().apply {
            put("tag", "dns-direct")
            put("type", "udp")
            put("server", directDnsServer)
            put("server_port", 53)
        })
        dns.put("servers", dnsServers)

        val dnsRules = JSONArray()
        // Route all standard A (IPv4) and AAAA (IPv6) domain lookups to FakeIP so the proxy server resolves them
        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                put("A")
                if (settings.ipMode != IpMode.IPV4_ONLY) {
                    put("AAAA")
                }
            })
            put("action", "route")
            put("server", "dns-fakeip")
            put("rewrite_ttl", 1)
        })
        // Reject AAAA queries when in IPV4_ONLY mode so browsers never attempt IPv6 connections
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            dnsRules.put(JSONObject().apply {
                put("query_type", JSONArray().apply { put("AAAA"); put("HTTPS") })
                put("action", "predefined")
                put("rcode", "NOERROR")
            })
        }
        dns.put("rules", dnsRules)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        if (hostIsIpv4) {
            dns.put("client_subnet", serverHost)
        }

        dns.put("final", "dns-remote")
        dns.put("reverse_mapping", true)
        root.put("dns", dns)

        // 3. Inbounds (Bind to "::" so both IPv4 and IPv6 REDIRECT/TPROXY work on Wi-Fi & LTE)
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
        })
        root.put("outbounds", outbounds)

        // 5. Routing Rules
        val route = JSONObject().apply {
            put("auto_detect_interface", false)
            put("default_domain_resolver", "dns-direct")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        // 1) Hijack all DNS entering dns-in or targeting port 53
        routeRules.put(JSONObject().apply {
            put("inbound", JSONArray().apply { put("dns-in") })
            put("action", "hijack-dns")
        })
        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(53) })
            put("action", "hijack-dns")
        })

        // 2) Reject Browser Built-In DoH (DNS-over-HTTPS) to public resolvers (8.8.8.8, 1.1.1.1, etc.)
        // so Chrome/Brave is forced to use system DNS (FakeIP) -> resolved by the remote proxy server!
        routeRules.put(JSONObject().apply {
            put("ip_cidr", JSONArray().apply {
                put("8.8.8.8/32")
                put("8.8.4.4/32")
                put("1.1.1.1/32")
                put("1.0.0.1/32")
                put("9.9.9.9/32")
                put("149.112.112.112/32")
                put("208.67.222.222/32")
                put("208.67.220.220/32")
                put("2001:4860:4860::8888/128")
                put("2001:4860:4860::8844/128")
                put("2606:4700:4700::1111/128")
                put("2606:4700:4700::1001/128")
            })
            put("port", JSONArray().apply { put(443); put(853) })
            put("action", "reject")
        })

        // 3) Reject ALL WebRTC STUN/TURN ports over both TCP and UDP (3478-3497, 5349-5350, 19302-19309)
        routeRules.put(JSONObject().apply {
            put("port_range", JSONArray().apply {
                put("3478:3497")
                put("5349:5350")
                put("19302:19309")
            })
            put("action", "reject")
        })

        // 4) Reject UDP 853 (DoT) and QUIC UDP 443/80 on TCP_ONLY or SOCKS5/SOCKS4/HTTP
        val rejectQuicUdp = settings.transportMode == TransportMode.TCP_ONLY ||
            settings.type == ProxyType.SOCKS5 ||
            settings.type == ProxyType.SOCKS4 ||
            settings.type == ProxyType.HTTP

        routeRules.put(JSONObject().apply {
            put("network", "udp")
            put("port", JSONArray().apply {
                put(853)
                if (rejectQuicUdp) {
                    put(80)
                    put(443)
                }
            })
            put("action", "reject")
        })

        // 5) Sniff protocol, TLS SNI, and HTTP Host
        routeRules.put(JSONObject().apply {
            put("action", "sniff")
            put("timeout", "300ms")
        })

        // 6) Reject STUN protocol if detected by sniffer on any port, and reject DoH domains
        routeRules.put(JSONObject().apply {
            put("protocol", JSONArray().apply { put("stun") })
            put("action", "reject")
        })
        routeRules.put(JSONObject().apply {
            put("domain", JSONArray().apply {
                put("dns.google")
                put("dns.google.com")
                put("cloudflare-dns.com")
                put("one.one.one.one")
                put("dns.quad9.net")
            })
            put("action", "reject")
        })

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
