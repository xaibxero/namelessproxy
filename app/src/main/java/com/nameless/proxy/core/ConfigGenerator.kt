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

    fun generateJson(
        settings: ProxySettings,
        inboundPort: Int,
        resolvedServerHost: String? = null
    ): String {
        val root = JSONObject()
        val rawCleanedHost = cleanHost(settings.host)
        val serverHost = resolvedServerHost?.takeIf { it.isNotBlank() } ?: rawCleanedHost
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot

        // 1. Logging
        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        // 2. Experimental Cache File (CRITICAL for sing-box 1.11/1.12 FakeIP reverse mapping!)
        // Without store_fakeip: true, sing-box cannot map 198.18.0.x back to the original domain name!
        root.put("experimental", JSONObject().apply {
            put("cache_file", JSONObject().apply {
                put("enabled", true)
                put("path", "/data/adb/nameless_proxy/cache_u${user}_s${slot}.db")
                put("store_fakeip", true)
            })
        })

        // 3. DNS Engine (Fast FakeIP + Remote TCP Tunnel through Proxy)
        val dns = JSONObject()
        val dnsServers = JSONArray()

        val remoteDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) "2606:4700:4700::1111" else "1.1.1.1"
        val directDnsServer = if (settings.ipMode == IpMode.IPV6_ONLY) BOOTSTRAP_DNS_IPV6 else BOOTSTRAP_DNS_IPV4

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

        // Hard-reject STUN/TURN and DoH canary domains at the DNS layer
        dnsRules.put(JSONObject().apply {
            put("domain_keyword", JSONArray().apply {
                put("stun")
                put("turn")
            })
            put("action", "reject")
        })

        dnsRules.put(JSONObject().apply {
            put("domain_suffix", JSONArray().apply {
                put("stun.l.google.com")
                put("stun1.l.google.com")
                put("stun2.l.google.com")
                put("stun3.l.google.com")
                put("stun4.l.google.com")
                put("stun.cloudflare.com")
                put("stun.services.mozilla.com")
                put("global.stun.twilio.com")
                put("use-application-dns.net")
                put("dns.google")
                put("dns.google.com")
                put("cloudflare-dns.com")
                put("one.one.one.one")
                put("1dot1dot1dot1.cloudflare-dns.com")
                put("dns.quad9.net")
                put("dns.nextdns.io")
                put("dns.adguard-dns.com")
                put("doh.opendns.com")
                put("dns.alidns.com")
                put("doh.pub")
            })
            put("action", "reject")
        })

        // IMPORTANT: Only reject HTTPS & SVCB query types here!
        // Do NOT put "AAAA" in action:"reject" because returning RCODE=REFUSED on AAAA causes
        // Android's netd resolver to think the DNS server failed and stall/retry for 10+ seconds!
        // Instead, dns.strategy = "ipv4_only" below natively returns instant NOERROR (0 answers) for AAAA!
        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                put("HTTPS")
                put("SVCB")
            })
            put("action", "reject")
        })

        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                put("A")
                put("AAAA")
            })
            put("action", "route")
            put("server", "dns-fakeip")
            put("rewrite_ttl", 60)
        })
        dns.put("rules", dnsRules)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        dns.put("reverse_mapping", true)
        root.put("dns", dns)

        // 4. Inbounds
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
            put("override_address", "1.1.1.1")
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

        // 5. Outbounds
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
                    put("server_name", settings.sni.ifEmpty { rawCleanedHost })
                })
            }
            ProxyType.HYSTERIA2 -> {
                proxyOutbound.put("type", "hysteria2")
                proxyOutbound.put("server", serverHost)
                proxyOutbound.put("server_port", settings.port)
                proxyOutbound.put("password", settings.password)
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
        // CRITICAL: default_domain_resolver is set to "dns-remote" (tunneled through proxy-out)
        // so sing-box NEVER resolves traffic domains via local ISP UDP!
        val route = JSONObject().apply {
            put("auto_detect_interface", false)
            put("default_domain_resolver", "dns-remote")
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

        // CRITICAL: override_destination: true ensures FakeIP (198.18.0.x) connections on redirect-in
        // have their destination rewritten to the sniffed TLS SNI / HTTP Host / FakeIP domain
        // so the remote proxy server resolves the domain at the exit node with 0ms local delay!
        routeRules.put(JSONObject().apply {
            put("action", "sniff")
            put("override_destination", true)
            put("timeout", "150ms")
        })

        // WebRTC & STUN protocol + port leak block
        routeRules.put(JSONObject().apply {
            put("protocol", JSONArray().apply {
                put("stun")
                put("quic")
            })
            put("action", "reject")
        })
        routeRules.put(JSONObject().apply {
            put("port_range", JSONArray().apply {
                put("3478:3497")
                put("5349:5350")
                put("19300:19320")
            })
            put("action", "reject")
        })

        // Reject DoT (853) queries inside sing-box
        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(853) })
            put("action", "reject")
        })

        // Reject DoH direct IP requests to known public DNS resolvers (forces port 53 hijack)
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
                put("94.140.14.14/32")
                put("94.140.15.15/32")
                put("2001:4860:4860::8888/128")
                put("2001:4860:4860::8844/128")
                put("2606:4700:4700::1111/128")
                put("2606:4700:4700::1001/128")
                put("2620:fe::fe/128")
                put("2620:fe::9/128")
            })
            put("port", JSONArray().apply { put(443); put(853) })
            put("action", "reject")
        })

        // Reject well-known DoH & STUN domain names
        routeRules.put(JSONObject().apply {
            put("domain_suffix", JSONArray().apply {
                put("dns.google")
                put("dns.google.com")
                put("cloudflare-dns.com")
                put("one.one.one.one")
                put("dns.quad9.net")
                put("dns.nextdns.io")
                put("dns.adguard-dns.com")
                put("doh.opendns.com")
                put("use-application-dns.net")
                put("stun.l.google.com")
                put("stun.cloudflare.com")
            })
            put("action", "reject")
        })

        // Block QUIC / HTTP3 UDP port 80/443/853 so browsers instantly use TCP 443 through the proxy
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
