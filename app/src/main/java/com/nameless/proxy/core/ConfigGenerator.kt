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

    const val BOOTSTRAP_DNS_IPV4 = "9.9.9.9"
    const val BOOTSTRAP_DNS_IPV6 = "2620:fe::fe"

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

        // 2. Official sing-box FakeIP DNS Engine
        // Returns a virtual IP (198.18.x.x) to the phone and passes the raw domain name
        // to proxy-out (with NO domain_resolver on proxy-out!) so the remote proxy server
        // resolves every domain using its own local residential ISP DNS!
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
        dnsRules.put(JSONObject().apply {
            put("query_type", JSONArray().apply {
                put("HTTPS")
                put("SVCB")
                if (settings.ipMode == IpMode.IPV4_ONLY) {
                    put("AAAA")
                }
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
            put("rewrite_ttl", 1)
        })
        dns.put("rules", dnsRules)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }

        dns.put("final", "dns-remote")
        dns.put("independent_cache", true)
        dns.put("reverse_mapping", true)
        root.put("dns", dns)

        // 3. Inbounds
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

        // 4. Outbounds
        // CRITICAL: Do NOT put "domain_resolver" on proxy-out!
        // In sing-box 1.12+, setting "domain_resolver" on a proxy outbound forces sing-box to resolve
        // the target domain locally into an IP before dialing the proxy! Leaving it off makes sing-box
        // send the domain name itself (SOCKS5 ATYP=0x03) to the proxy so the proxy's own ISP resolves it!
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

        routeRules.put(JSONObject().apply {
            put("inbound", JSONArray().apply { put("dns-in") })
            put("action", "hijack-dns")
        })
        routeRules.put(JSONObject().apply {
            put("port", JSONArray().apply { put(53) })
            put("action", "hijack-dns")
        })

        // Sniff TLS SNI & HTTP Host and override destination to the sniffed domain name!
        routeRules.put(JSONObject().apply {
            put("action", "sniff")
            put("override_destination", true)
            put("timeout", "300ms")
        })

        routeRules.put(JSONObject().apply {
            put("protocol", JSONArray().apply { put("stun") })
            put("action", "reject")
        })
        routeRules.put(JSONObject().apply {
            put("port_range", JSONArray().apply {
                put("3478:3497")
                put("5349:5350")
                put("19302:19309")
            })
            put("action", "reject")
        })

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

        route.put("rules", routeRules)
        root.put("route", route)

        return root.toString(2)
    }
}
