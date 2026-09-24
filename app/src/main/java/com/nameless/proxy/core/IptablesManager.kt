package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(inboundPort: Int, selectedUids: List<Int>? = null): List<String> {
        val chain = ProfileManager.chainName
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val commands = mutableListOf<String>()

        // 1. Clean up any stale chains
        commands.add("iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain 2>/dev/null")
        commands.add("iptables -t nat -F $chain 2>/dev/null")
        commands.add("iptables -t nat -X $chain 2>/dev/null")

        // 2. Create the profile's dedicated chain
        commands.add("iptables -t nat -N $chain")

        // 3. Bypass reserved, private LAN, and loopback subnets
        val reservedRanges = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )
        for (range in reservedRanges) {
            commands.add("iptables -t nat -A $chain -d $range -j RETURN")
        }

        // 4. Critical: Bypass Root (UID 0) to avoid sing-box routing loop
        commands.add("iptables -t nat -A $chain -m owner --uid-owner 0 -j RETURN")

        // 5. Apply redirection logic
        if (selectedUids.isNullOrEmpty()) {
            // Whole Profile mode
            commands.add("iptables -t nat -A $chain -p tcp -j REDIRECT --to-ports $inboundPort")
        } else {
            // Whitelist mode (Proxy ONLY selected app UIDs)
            for (uid in selectedUids) {
                commands.add("iptables -t nat -A $chain -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
            }
        }

        // 6. Hook our chain into OUTPUT for this profile's UID range
        commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain")

        return commands
    }

    fun generateDisableCommands(): List<String> {
        val chain = ProfileManager.chainName
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        return listOf(
            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chain 2>/dev/null",
            "iptables -t nat -F $chain 2>/dev/null",
            "iptables -t nat -X $chain 2>/dev/null"
        )
    }
}
