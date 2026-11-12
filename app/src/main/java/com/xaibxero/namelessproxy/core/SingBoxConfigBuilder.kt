package com.xaibxero.namelessproxy.core

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {

    fun build(slot: ProxySlot): String {
        val cleanHost = slot.host.trim()

        val outbound = JSONObject().apply {
            put("tag", "proxy-out")
            put("routing_mark", ProfileContext.outboundBypassMark)
            put("server", cleanHost)
            put("server_port", slot.port)

            when (slot.protocol) {
                ProxyProtocol.SOCKS5 -> {
                    put("type", "socks")
                    put("version", "5")
                    if (slot.username.isNotBlank()) {
                        put("username", slot.username)
                        put("password", slot.password)
                    }
                }
                ProxyProtocol.SOCKS4 -> {
                    put("type", "socks")
                    put("version", "4")
                    if (slot.username.isNotBlank()) put("username", slot.username)
                }
                ProxyProtocol.SOCKS4A -> {
                    put("type", "socks")
                    put("version", "4a")
                    if (slot.username.isNotBlank()) put("username", slot.username)
                }
                ProxyProtocol.HTTP -> {
                    put("type", "http")
                    if (slot.username.isNotBlank()) {
                        put("username", slot.username)
                        put("password", slot.password)
                    }
                }
                ProxyProtocol.HTTPS -> {
                    put("type", "http")
                    if (slot.username.isNotBlank()) {
                        put("username", slot.username)
                        put("password", slot.password)
                    }
                    put("tls", JSONObject().apply {
                        put("enabled", true)
                        if (slot.sni.isNotBlank()) put("server_name", slot.sni.trim())
                    })
                }
                ProxyProtocol.SHADOWSOCKS -> {
                    put("type", "shadowsocks")
                    put("method", slot.methodOrFlow.ifBlank { "2022-blake3-aes-128-gcm" }.trim())
                    put("password", slot.password)
                }
                ProxyProtocol.VLESS -> {
                    put("type", "vless")
                    put("uuid", slot.password.trim())
                    if (slot.methodOrFlow.isNotBlank()) {
                        put("flow", slot.methodOrFlow.trim())
                    }
                    if (slot.security.equals("tls", true) || slot.security.equals("reality", true)) {
                        put("tls", buildTlsObject(slot, isReality = slot.security.equals("reality", true)))
                    }
                    buildTransportObject(slot)?.let { put("transport", it) }
                }
                ProxyProtocol.TROJAN -> {
                    put("type", "trojan")
                    put("password", slot.password)
                    put("tls", buildTlsObject(slot, isReality = false))
                    buildTransportObject(slot)?.let { put("transport", it) }
                }
            }
        }

        val root = JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "info")
                put("timestamp", true)
            })

            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "remote-doh")
                        put("address", "https://1.1.1.1/dns-query")
                        put("strategy", "ipv4_only")
                        put("detour", "proxy-out")
                    })
                    put(JSONObject().apply {
                        put("tag", "direct-dns")
                        put("address", "1.1.1.1")
                        put("strategy", "ipv4_only")
                        put("detour", "direct-out")
                    })
                    put(JSONObject().apply {
                        put("tag", "fakeip-dns")
                        put("address", "fakeip")
                    })
                })
                put("rules", JSONArray().apply {
                    // Resolve outbound server hostnames directly (with routing_mark) to avoid DNS deadlock
                    put(JSONObject().apply {
                        put("outbound", "any")
                        put("server", "direct-dns")
                    })
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
                put("final", "remote-doh")
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
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct-out")
                    put("routing_mark", ProfileContext.outboundBypassMark)
                })
                put(JSONObject().put("type", "dns").put("tag", "dns-out"))
                put(JSONObject().put("type", "block").put("tag", "block-out"))
            })

            put("route", JSONObject().apply {
                put("auto_detect_interface", false)
                put("rules", JSONArray().apply {
                    put(JSONObject().put("inbound", "dns-in").put("outbound", "dns-out"))
                    put(JSONObject().put("protocol", "dns").put("outbound", "dns-out"))
                })
                put("final", "proxy-out")
            })
        }

        return root.toString(2)
    }

    private fun buildTlsObject(slot: ProxySlot, isReality: Boolean): JSONObject = JSONObject().apply {
        put("enabled", true)
        val serverName = slot.sni.ifBlank { slot.host }.trim()
        if (serverName.isNotBlank()) put("server_name", serverName)
        if (isReality || slot.realityPublicKey.isNotBlank()) {
            put("utls", JSONObject().apply {
                put("enabled", true)
                put("fingerprint", "chrome")
            })
            put("reality", JSONObject().apply {
                put("enabled", true)
                put("public_key", slot.realityPublicKey.trim())
                if (slot.realityShortId.isNotBlank()) {
                    put("short_id", slot.realityShortId.trim())
                }
            })
        }
    }

    private fun buildTransportObject(slot: ProxySlot): JSONObject? {
        return when (slot.transport.lowercase().trim()) {
            "ws" -> JSONObject().apply {
                put("type", "ws")
                put("path", slot.wsPathOrService.ifBlank { "/" })
                if (slot.sni.isNotBlank()) {
                    put("headers", JSONObject().put("Host", slot.sni.trim()))
                }
            }
            "grpc" -> JSONObject().apply {
                put("type", "grpc")
                put("service_name", slot.wsPathOrService.ifBlank { "grpc" })
            }
            else -> null
        }
    }
}
