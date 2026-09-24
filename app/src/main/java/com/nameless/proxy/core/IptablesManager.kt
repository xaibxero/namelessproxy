package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(inboundPort: Int, selectedUids: List<Int>? = null): List<String> {
        val chain = ProfileManager.chainName
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val commands = mutableListOf<String>()

        // ==========================================
        // 1. IPv4 IPTABLES SETUP
        // ==========================================
        // Clean up stale IPv4 rules
        commands.add("iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain 2>/dev/null")
        commands.add("iptables -t nat -F $chain 2>/dev/null")
        commands.add("iptables -t nat -X $chain 2>/dev/null")

        // Create dedicated IPv4 chain
        commands.add("iptables -t nat -N $chain")

        // Bypass private, LAN, and loopback ranges
        val reservedRanges = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )
        for (range in reservedRanges) {
            commands.add("iptables -t nat -A $chain -d $range -j RETURN")
        }

        // Bypass Root (UID 0) to avoid sing-box routing loop
        commands.add("iptables -t nat -A $chain -m owner --uid-owner 0 -j RETURN")

        // Apply IPv4 redirection rules
        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chain -p tcp -j REDIRECT --to-ports $inboundPort")
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -t nat -A $chain -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
            }
        }

        // Hook chain into IPv4 OUTPUT table
        commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain")

        // ==========================================
        // 2. IPv6 LEAK PROTECTION (ip6tables)
        // ==========================================
        // Block IPv6 for targeted UIDs to force immediate IPv4 fallback without leaks
        commands.add("ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null")
        if (selectedUids.isNullOrEmpty()) {
            commands.add("ip6tables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp6-port-unreachable")
        } else {
            for (uid in selectedUids) {
                commands.add("ip6tables -D OUTPUT -m owner --uid-owner $uid -j REJECT 2>/dev/null")
                commands.add("ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp6-port-unreachable")
            }
        }

        return commands
    }

    fun generateDisableCommands(): List<String> {
        val chain = ProfileManager.chainName
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        return listOf(
            // Remove IPv4 redirection
            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain 2>/dev/null",
            "iptables -t nat -F $chain 2>/dev/null",
            "iptables -t nat -X $chain 2>/dev/null",

            // Unblock IPv6
            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null"
        )
    }
}
