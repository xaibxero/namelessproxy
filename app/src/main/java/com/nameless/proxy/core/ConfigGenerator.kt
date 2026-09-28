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

    /**
     * Android UID layout: user N owns UIDs N*100000 .. N*100000+99999.
     *  - N*100000 + 0..9999      = system / radio / netd / shell (must NOT be tunnelled)
     *  - N*100000 + 10000..99999 = normal apps
     */
    private fun systemUidRanges(currentUser: Int): JSONArray {
        val arr = JSONArray()
        arr.put("0:9999")
        if (currentUser > 0) {
            val start = currentUser * 100000
            arr.put("$start:${start + 9999}")
        }
        return arr
    }

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

        // 2. DNS
        //    dns-local  : only used to resolve the proxy server's own hostname (must go direct,
        //                 otherwise resolving the proxy host would need the proxy = loop)
        //    dns-remote : used for everything hijacked from apps, sent through the proxy
        val dns = JSONObject()
        val dnsServers = JSONArray()

        dnsServers.put(JSONObject().apply {
            put("type", "local")
            put("tag", "dns-local")
        })
        dnsServers.put(JSONObject().apply {
            put("type", "tcp")
            put("tag", "dns-remote")
            put("server", "1.1.1.1")
            put("server_port", 53)
            put("detour", "proxy-out")
        })
        dns.put("servers", dnsServers)

        when (settings.ipMode) {
            IpMode.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            IpMode.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
            IpMode.DUAL_STACK -> dns.put("strategy", "prefer_ipv4")
        }
        dns.put("final", "dns-remote")
        root.put("dns", dns)

        // 3. Inbounds
        val inbounds = JSONArray()

        // Unique per Android user + slot so several profiles can run at the same time
        val ifaceName = "nlp${user}s${slot}"
        val v4 = "172.19.${user % 200}.${slot * 4 + 1}/30"
        val v6 = "fdfe:dcba:9876:${Integer.toHexString(slotId + 1)}::1/126"

        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", ifaceName)
            put("address", JSONArray().apply {
                put(v4)
                put(v6)   // IPv6 goes INTO the tunnel too, otherwise real IPv6 leaks (WebRTC)
            })
            put("auto_route", true)
            // strict_route makes every non-covered network "unreachable" and can break
            // Android's own connectivity checks on cellular -> keep it off
            put("strict_route", false)
            put("iproute2_table_index", 3000 + slotId)
            put("iproute2_rule_index", 9000 + slotId * 10)
            // NOTE: no "stack" field on purpose.
            //  - "mixed"/"gvisor" need the with_gvisor build tag and crash without it
            //  - "stack" is deprecated from sing-box 1.15 and removed in 1.17
            //  - when omitted, sing-box picks a stack that works for the binary it is running

            if (settings.routeHotspot) {
                // Global mode: everything (all apps of all users + hotspot clients) goes through
                // the tunnel, but system / radio / netd UIDs stay direct so the modem and
                // Android's connectivity validation are never touched.
                put("exclude_uid_range", systemUidRanges(user))
            } else {
                // Profile mode: only the apps of THIS Android user go through this proxy.
                val start = user * 100000
                put("include_uid_range", JSONArray().apply {
                    put("${start + 10000}:${start + 99999}")
                })
            }
        }
        inbounds.put(tunInbound)

        // Local SOCKS listener (used by the in-app proxy tester)
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
                        // Reality needs the with_utls build tag in the sing-box binary
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
                // Hysteria2 needs the with_quic build tag in the sing-box binary
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

        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        })
        root.put("outbounds", outbounds)

        // 5. Routing
        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            // resolves the proxy server hostname (if it is a domain) WITHOUT using the proxy
            put("default_domain_resolver", "dns-local")
            put("final", "proxy-out")
        }

        val routeRules = JSONArray()

        // sniff must be first so the following rules can match "protocol": "dns"
        routeRules.put(JSONObject().apply { put("action", "sniff") })

        routeRules.put(JSONObject().apply {
            put("protocol", "dns")
            put("action", "hijack-dns")
        })

        // LAN / private ranges stay direct
        routeRules.put(JSONObject().apply {
            put("ip_is_private", true)
            put("outbound", "direct-out")
        })

        // The proxy server itself must never be routed back into the proxy
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
