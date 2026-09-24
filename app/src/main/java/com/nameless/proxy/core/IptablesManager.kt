package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val chainNatV4 = ProfileManager.chainName
        val chainNatV6 = "${ProfileManager.chainName}_V6"
        val chainPreMangle = "NAMELESS_PRE_U${ProfileManager.profileId}"
        val chainOutMangle = "NAMELESS_OUT_U${ProfileManager.profileId}"
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val tableId = 100 + ProfileManager.profileId
        val markHex = "0x" + Integer.toHexString(0x2333 + ProfileManager.profileId)

        val commands = mutableListOf<String>()

        // 1. Cleanup all existing rules
        commands.addAll(generateDisableCommands())

        // 2. Setup Policy Routing for UDP TPROXY
        if (settings.transportMode == TransportMode.TCP_AND_UDP && settings.type == ProxyType.SOCKS5) {
            commands.add("ip rule add fwmark $markHex table $tableId pref 100")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

            if (settings.ipMode != IpMode.IPV4_ONLY) {
                commands.add("ip -6 rule add fwmark $markHex table $tableId pref 100")
                commands.add("ip -6 route add local ::/0 dev lo table $tableId")
            }

            // MANGLE PREROUTING: Intercept marked UDP packets into tproxy listener
            commands.add("iptables -t mangle -N $chainPreMangle")
            commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $inboundPort --tproxy-mark $markHex")
            commands.add("iptables -t mangle -A PREROUTING -j $chainPreMangle")

            // MANGLE OUTPUT: Mark outbound UDP packets from target UIDs
            commands.add("iptables -t mangle -N $chainOutMangle")
            commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0 -j RETURN")

            // HIJACK UDP PORT 53 FIRST: Intercept DNS even if sent to local router (192.168.x.x)
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }

            // Bypass reserved subnets for other UDP
            val reservedV4 = listOf(
                "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
                "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
            )
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
            }
            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t mangle -A $chainOutMangle -d ${settings.host} -j RETURN")
            }

            // Mark remaining UDP traffic (WebRTC / Streams)
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }
            commands.add("iptables -t mangle -A OUTPUT -m owner --uid-owner $start-$end -j $chainOutMangle")
        }

        // 3. Configure IPv4 TCP Redirection (NAT Table)
        if (settings.ipMode == IpMode.IPV6_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp-port-unreachable")
                }
            }
        } else {
            commands.add("iptables -t nat -N $chainNatV4")
            commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0 -j RETURN")

            // HIJACK TCP PORT 53 FIRST: Intercept TCP DNS before subnet bypass
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }

            // Bypass local / reserved subnets for other TCP
            val reservedV4 = listOf(
                "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
                "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
            )
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
            }

            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host} -j RETURN")
            }

            // Redirect all other profile TCP streams
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV4")
        }

        // 4. Configure IPv6 TCP Redirection / Leak Shield
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A OUTPUT -m owner --uid-owner $start-$end -j REJECT --reject-with icmp6-port-unreachable")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT --reject-with icmp6-port-unreachable")
                }
            }
        } else {
            commands.add("ip6tables -t nat -N $chainNatV6")
            commands.add("ip6tables -t nat -A $chainNatV6 -m owner --uid-owner 0 -j RETURN")

            // Intercept IPv6 TCP DNS
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }

            commands.add("ip6tables -t nat -A $chainNatV6 -d ::1/128 -j RETURN")
            commands.add("ip6tables -t nat -A $chainNatV6 -d fe80::/10 -j RETURN")

            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("ip6tables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV6")
        }

        return commands
    }

    fun generateDisableCommands(): List<String> {
        val chainNatV4 = ProfileManager.chainName
        val chainNatV6 = "${ProfileManager.chainName}_V6"
        val chainPreMangle = "NAMELESS_PRE_U${ProfileManager.profileId}"
        val chainOutMangle = "NAMELESS_OUT_U${ProfileManager.profileId}"
        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val tableId = 100 + ProfileManager.profileId
        val markHex = "0x" + Integer.toHexString(0x2333 + ProfileManager.profileId)

        return listOf(
            // NAT IPv4 cleanup
            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV4 2>/dev/null",
            "iptables -t nat -F $chainNatV4 2>/dev/null",
            "iptables -t nat -X $chainNatV4 2>/dev/null",
            "iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null",

            // MANGLE UDP cleanup
            "iptables -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null",
            "iptables -t mangle -F $chainPreMangle 2>/dev/null",
            "iptables -t mangle -X $chainPreMangle 2>/dev/null",
            "iptables -t mangle -D OUTPUT -m owner --uid-owner $start-$end -j $chainOutMangle 2>/dev/null",
            "iptables -t mangle -F $chainOutMangle 2>/dev/null",
            "iptables -t mangle -X $chainOutMangle 2>/dev/null",

            // Routing table cleanup
            "ip rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip route flush table $tableId 2>/dev/null",
            "ip -6 rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip -6 route flush table $tableId 2>/dev/null",

            // NAT IPv6 cleanup
            "ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV6 2>/dev/null",
            "ip6tables -t nat -F $chainNatV6 2>/dev/null",
            "ip6tables -t nat -X $chainNatV6 2>/dev/null",
            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null"
        )
    }
}
