package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        ipMode: IpMode,
        selectedUids: List<Int>? = null
    ): List<String> {
        val chainV4 = ProfileManager.chainName
        val chainV6 = "${ProfileManager.chainName}_V6"
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val commands = mutableListOf<String>()

        // ========================================================
        // 1. CLEANUP PREVIOUS RULES (IPv4 & IPv6)
        // ========================================================
        commands.add("iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV4 2>/dev/null")
        commands.add("iptables -t nat -F $chainV4 2>/dev/null")
        commands.add("iptables -t nat -X $chainV4 2>/dev/null")
        commands.add("iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null")

        commands.add("ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV6 2>/dev/null")
        commands.add("ip6tables -t nat -F $chainV6 2>/dev/null")
        commands.add("ip6tables -t nat -X $chainV6 2>/dev/null")
        commands.add("ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null")

        // ========================================================
        // 2. CONFIGURE IPv4 ROUTING
        // ========================================================
        if (ipMode == IpMode.IPV6_ONLY) {
            // Drop IPv4 completely to prevent non-IPv6 traffic from bypassing
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp-port-unreachable")
                }
            }
        } else {
            // IPV4_ONLY or DUAL_STACK: Setup IPv4 REDIRECT
            commands.add("iptables -t nat -N $chainV4")

            val reservedV4 = listOf(
                "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
                "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
            )
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainV4 -d $range -j RETURN")
            }

            // Prevent sing-box (root UID 0) from self-looping
            commands.add("iptables -t nat -A $chainV4 -m owner --uid-owner 0 -j RETURN")

            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t nat -A $chainV4 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t nat -A $chainV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV4")
        }

        // ========================================================
        // 3. CONFIGURE IPv6 ROUTING
        // ========================================================
        if (ipMode == IpMode.IPV4_ONLY) {
            // Drop IPv6 completely to eliminate carrier IPv6 leaks
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp6-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp6-port-unreachable")
                }
            }
        } else {
            // DUAL_STACK or IPV6_ONLY: Setup IPv6 REDIRECT
            commands.add("ip6tables -t nat -N $chainV6")

            // Bypass IPv6 loopback (::1) and link-local (fe80::/10)
            commands.add("ip6tables -t nat -A $chainV6 -d ::1/128 -j RETURN")
            commands.add("ip6tables -t nat -A $chainV6 -d fe80::/10 -j RETURN")
            commands.add("ip6tables -t nat -A $chainV6 -m owner --uid-owner 0 -j RETURN")

            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainV6 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainV6 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("ip6tables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV6")
        }

        return commands
    }

    fun generateDisableCommands(): List<String> {
        val chainV4 = ProfileManager.chainName
        val chainV6 = "${ProfileManager.chainName}_V6"
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        return listOf(
            // Clear IPv4 NAT chain & reject rules
            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV4 2>/dev/null",
            "iptables -t nat -F $chainV4 2>/dev/null",
            "iptables -t nat -X $chainV4 2>/dev/null",
            "iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null",

            // Clear IPv6 NAT chain & reject rules
            "ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV6 2>/dev/null",
            "ip6tables -t nat -F $chainV6 2>/dev/null",
            "ip6tables -t nat -X $chainV6 2>/dev/null",
            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null"
        )
    }
}
