package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val chainV4 = ProfileManager.chainName
        val chainV6 = "${ProfileManager.chainName}_V6"
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val commands = mutableListOf<String>()

        // 1. Clean previous rules
        commands.add("iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV4 2>/dev/null")
        commands.add("iptables -t nat -F $chainV4 2>/dev/null")
        commands.add("iptables -t nat -X $chainV4 2>/dev/null")
        commands.add("iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null")

        commands.add("ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV6 2>/dev/null")
        commands.add("ip6tables -t nat -F $chainV6 2>/dev/null")
        commands.add("ip6tables -t nat -X $chainV6 2>/dev/null")
        commands.add("ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null")

        // 2. Configure IPv4 Routing
        if (settings.ipMode == IpMode.IPV6_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp-port-unreachable")
                }
            }
        } else {
            commands.add("iptables -t nat -N $chainV4")

            // Reserved / Local subnets
            val reservedV4 = listOf(
                "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
                "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
            )
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainV4 -d $range -j RETURN")
            }

            // Upstream proxy server IP bypass (Prevents loopback)
            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t nat -A $chainV4 -d ${settings.host} -j RETURN")
            }

            // Root process (sing-box UID 0) bypass
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

        // 3. Configure IPv6 Routing
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp6-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp6-port-unreachable")
                }
            }
        } else {
            commands.add("ip6tables -t nat -N $chainV6")
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
            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV4 2>/dev/null",
            "iptables -t nat -F $chainV4 2>/dev/null",
            "iptables -t nat -X $chainV4 2>/dev/null",
            "iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null",

            "ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainV6 2>/dev/null",
            "ip6tables -t nat -F $chainV6 2>/dev/null",
            "ip6tables -t nat -X $chainV6 2>/dev/null",
            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null"
        )
    }
}
