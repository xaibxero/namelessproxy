package com.xaibxero.namelessproxy.core

import kotlinx.serialization.Serializable

@Serializable
enum class ProxyProtocol {
    SOCKS5, SOCKS4, SOCKS4A, HTTP, HTTPS
}

@Serializable
enum class Ipv6Mode {
    BRIDGE_OVER_IPV4,
    NATIVE_DUAL_STACK,
    STRICT_BLOCK
}

@Serializable
data class ProxySlot(
    val index: Int,
    val label: String = "Slot ${index + 1}",
    val protocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
)

@Serializable
data class ProfileSettings(
    val activeSlot: Int = 0,
    val bootOnStartup: Boolean = false,
    val shareHotspot: Boolean = false,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.BRIDGE_OVER_IPV4,
    val slots: List<ProxySlot> = List(5) { ProxySlot(index = it) }
)
