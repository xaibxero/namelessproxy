package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val commands = mutableListOf<String>()

        // Clean up old rules for this user and slot
        commands.addAll(generateDisableCommands(user, slot))

        // Enable IP forwarding for hotspot / tethering clients if enabled
        if (settings.routeHotspot) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            commands.add("iptables -A FORWARD -j ACCEPT 2>/dev/null")
            commands.add("ip6tables -A FORWARD -j ACCEPT 2>/dev/null")
        }

        return commands
    }

    // Overload 1: Handles zero-argument calls (e.g. generateDisableCommands())
    fun generateDisableCommands(): List<String> {
        return generateDisableCommands(ProfileManager.androidUserId, ProfileManager.activeSlot)
    }

    // Overload 2: Handles two-argument calls from BootManager and ProxyController (e.g. generateDisableCommands(user, slot))
    fun generateDisableCommands(
        user: Int,
        slot: Int
    ): List<String> {
        return listOf(
            "iptables -D FORWARD -j ACCEPT 2>/dev/null",
            "ip6tables -D FORWARD -j ACCEPT 2>/dev/null"
        )
    }
}
