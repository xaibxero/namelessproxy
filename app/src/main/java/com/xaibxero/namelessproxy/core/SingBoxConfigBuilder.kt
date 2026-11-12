package com.xaibxero.namelessproxy.core

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {

    fun build(slot: ProxySlot, settings: ProfileSettings): String {
        val domainStrategy = when (settings.ipv6Mode) {
            Ipv6Mode.BRIDGE_OVER_IPV4 -> "ipv4_only"
            Ipv6Mode.NATIVE_DUAL_STACK -> "prefer_ipv6"
            Ipv6Mode.STRICT_BLOCK -> "ipv4_only"
        }

        val outbound = JSONObject().apply {
            put("tag", "proxy-out")
            put("routing_mark", ProfileContext.outboundBypassMark)
            put("server", slot.host.trim())
            put("server_port", slot.port)

            when (slot.protocol) {
                ProxyProtocol.SOCKS5 -> {
                    put("type", "socks")
                    put("version", "5")
                }
                ProxyProtocol.SOCKS4 -> {
                    put("type", "socks")
                    put("version", "4")
                }
                ProxyProtocol.SOCKS4A -> {
                    put("type", "socks")
                    put("version", "4a")
                }
                ProxyProtocol.HTTP -> {
                    put("type", "http")
                }
                ProxyProtocol.HTTPS -> {
                    put("type", "http")
                    put("tls", JSONObject().apply {
                        put("enabled", true)
                    })
                }
            }

            if (slot.username.isNotBlank()) {
                put("username", slot.username)
                put("password", slot.password)
            }
        }

        val root = JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "warn")
                put("timestamp", true)
            })

            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "remote-doh")
                        put("address", "https://1.1.1.1/dns-query")
                        put("address_resolver", "bootstrap-dns")
                        put("strategy", domainStrategy)
                        put("detour", "proxy-out")
                    })
                    put(JSONObject().apply {
                        put("tag", "bootstrap-dns")
                        put("address", "1.1.1.1")
                        put("detour", "proxy-out")
                    })
                    put(JSONObject().apply {
                        put("tag", "fakeip-dns")
                        put("address", "fakeip")
                    })
                })
                put("rules", JSONArray().apply {
                    put(JSONObject().apply {
                        put("query_type", JSONArray().put("A").put("AAAA"))
                        put("server", "fakeip-dns")
                    })
                })
                put("fakeip", JSONObject().apply {
                    put("enabled", true)
                    put("inet4_range", "198.18.0.0/15")
                    put("inet6_range", "fc00::/18")
                })
                put("independent_cache", true)
            })

            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tproxy")
                    put("tag", "tproxy-in")
                    put("listen", "::")
                    put("listen_port", ProfileContext.tproxyPort)
                    put("sniff", true)
                    put("sniff_override_destination", true)
                })
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "dns-in")
                    put("listen", "::")
                    put("listen_port", ProfileContext.dnsPort)
                })
            })

            put("outbounds", JSONArray().apply {
                put(outbound)
                put(JSONObject().put("type", "dns").put("tag", "dns-out"))
                put(JSONObject().put("type", "block").put("tag", "block-out"))
            })

            put("route", JSONObject().apply {
                put("auto_detect_interface", false)
                put("rules", JSONArray().apply {
                    put(JSONObject().put("inbound", "dns-in").put("outbound", "dns-out"))
                    put(JSONObject().put("protocol", "dns").put("outbound", "dns-out"))
                    if (settings.ipv6Mode == Ipv6Mode.STRICT_BLOCK) {
                        put(JSONObject().put("ip_version", 6).put("outbound", "block-out"))
                    }
                })
                put("final", "proxy-out")
            })
        }

        return root.toString(2)
    }
}
