package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val slot = ProfileManager.profileId
        val chainNatV4 = ProfileManager.chainName
        val chainNatV6 = "${ProfileManager.chainName}_V6"
        val chainPreMangle = "NAMELESS_PRE_U$slot"
        val chainOutMangle = "NAMELESS_OUT_U$slot"
        val chainHotspotNat = "NAMELESS_HS_NAT_U$slot"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_U$slot"
        val chainHotspotV6Block = "NAMELESS_HS_V6_U$slot"

        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val tableId = 100 + slot
        val markHex = "0x" + Integer.toHexString(0x2333 + slot)

        val commands = mutableListOf<String>()

        // 1. Clean existing rules for this slot
        commands.addAll(generateDisableCommands(slot))

        // 2. Policy Routing for UDP TPROXY
        if (settings.transportMode == TransportMode.TCP_AND_UDP) {
            commands.add("ip rule add fwmark $markHex table $tableId pref 100")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

            if (settings.ipMode != IpMode.IPV4_ONLY) {
                commands.add("ip -6 rule add fwmark $markHex table $tableId pref 100")
                commands.add("ip -6 route add local ::/0 dev lo table $tableId")
            }

            commands.add("iptables -t mangle -N $chainPreMangle")
            commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $inboundPort --tproxy-mark $markHex")
            commands.add("iptables -t mangle -A PREROUTING -j $chainPreMangle")

            commands.add("iptables -t mangle -N $chainOutMangle")
            commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0 -j RETURN")

            // DNS hijacking
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }

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

            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }
            commands.add("iptables -t mangle -A OUTPUT -m owner --uid-owner $start-$end -j $chainOutMangle")
        }

        // 3. IPv4 TCP Redirection (NAT Table)
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

            // DNS hijacking
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }

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

            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV4")
        }

        // 4. IPv6 Redirection / Leak Shield
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

        // 5. Hotspot & Tethering Routing
        if (settings.routeHotspot) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward")
            commands.add("echo 0 > /proc/sys/net/ipv6/conf/all/forwarding 2>/dev/null")

            val hotspotGateways = listOf("192.168.42.1", "192.168.43.1", "192.168.44.1", "192.168.49.1", "192.168.50.1")
            val hotspotSubnets = listOf(
                "192.168.42.0/24", "192.168.43.0/24", "192.168.44.0/24", "192.168.49.0/24", "192.168.50.0/24"
            )
            val tetherInterfaces = listOf("ap+", "rndis+", "usb+", "softap+", "wlan1", "wlan2", "bt-pan+")

            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD -j $chainHotspotV6Block")

            commands.add("iptables -t nat -N $chainHotspotNat")
            commands.add("iptables -t nat -A $chainHotspotNat -i lo -j RETURN")
            commands.add("iptables -t nat -A $chainHotspotNat -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")

            for (gw in hotspotGateways) {
                commands.add("iptables -t nat -A $chainHotspotNat -d $gw -j RETURN")
            }
            for (subnet in hotspotSubnets) {
                commands.add("iptables -t nat -A $chainHotspotNat -s $subnet -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -t nat -A PREROUTING -j $chainHotspotNat")

            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                commands.add("iptables -t mangle -N $chainHotspotMangle")
                commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
                commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j TPROXY --on-port $inboundPort --tproxy-mark $markHex")

                for (gw in hotspotGateways) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -d $gw -j RETURN")
                }
                for (subnet in hotspotSubnets) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -s $subnet -p udp -j TPROXY --on-port $inboundPort --tproxy-mark $markHex")
                }
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $inboundPort --tproxy-mark $markHex")
                }
                commands.add("iptables -t mangle -A PREROUTING -j $chainHotspotMangle")
            }
        }

        return commands
    }

    fun generateDisableCommands(slot: Int = ProfileManager.profileId): List<String> {
        val chainNatV4 = "NAMELESS_P$slot"
        val chainNatV6 = "NAMELESS_P${slot}_V6"
        val chainPreMangle = "NAMELESS_PRE_U$slot"
        val chainOutMangle = "NAMELESS_OUT_U$slot"
        val chainHotspotNat = "NAMELESS_HS_NAT_U$slot"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_U$slot"
        val chainHotspotV6Block = "NAMELESS_HS_V6_U$slot"

        val start = ProfileManager.uidStart
        val end = ProfileManager.uidEnd

        val tableId = 100 + slot
        val markHex = "0x" + Integer.toHexString(0x2333 + slot)

        return listOf(
            "ip6tables -D FORWARD -j $chainHotspotV6Block 2>/dev/null",
            "ip6tables -F $chainHotspotV6Block 2>/dev/null",
            "ip6tables -X $chainHotspotV6Block 2>/dev/null",

            "iptables -t nat -D PREROUTING -j $chainHotspotNat 2>/dev/null",
            "iptables -t nat -F $chainHotspotNat 2>/dev/null",
            "iptables -t nat -X $chainHotspotNat 2>/dev/null",
            "iptables -t mangle -D PREROUTING -j $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -F $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -X $chainHotspotMangle 2>/dev/null",

            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV4 2>/dev/null",
            "iptables -t nat -F $chainNatV4 2>/dev/null",
            "iptables -t nat -X $chainNatV4 2>/dev/null",
            "iptables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null",

            "iptables -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null",
            "iptables -t mangle -F $chainPreMangle 2>/dev/null",
            "iptables -t mangle -X $chainPreMangle 2>/dev/null",
            "iptables -t mangle -D OUTPUT -m owner --uid-owner $start-$end -j $chainOutMangle 2>/dev/null",
            "iptables -t mangle -F $chainOutMangle 2>/dev/null",
            "iptables -t mangle -X $chainOutMangle 2>/dev/null",

            "ip rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip route flush table $tableId 2>/dev/null",
            "ip -6 rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip -6 route flush table $tableId 2>/dev/null",

            "ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV6 2>/dev/null",
            "ip6tables -t nat -F $chainNatV6 2>/dev/null",
            "ip6tables -t nat -X $chainNatV6 2>/dev/null",
            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j REJECT 2>/dev/null"
        )
    }

    fun generateDisableAllCommands(): List<String> {
        val cmds = mutableListOf<String>()
        for (i in 0 until ProfileManager.MAX_PROFILES) {
            cmds.addAll(generateDisableCommands(i))
        }
        return cmds
    }
}
