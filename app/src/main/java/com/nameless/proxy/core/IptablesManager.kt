package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val commands = mutableListOf<String>()
        commands.addAll(generateDisableCommands())

        if (settings.routeHotspot) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            commands.add("iptables -A FORWARD -j ACCEPT 2>/dev/null")
            commands.add("ip6tables -A FORWARD -j ACCEPT 2>/dev/null")
        }

        return commands
    }

    fun generateDisableCommands(): List<String> {
        return listOf(
            "iptables -D FORWARD -j ACCEPT 2>/dev/null",
            "ip6tables -D FORWARD -j ACCEPT 2>/dev/null"
        )
    }
}
