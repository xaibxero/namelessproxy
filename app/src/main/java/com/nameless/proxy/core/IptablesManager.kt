package com.nameless.proxy.core

object IptablesManager {

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val key = "u${user}_s$slot"

        val tproxyPort = inboundPort + 8
        val chainNatV4 = "NAMELESS_U${user}_S$slot"
        val chainNatV6 = "NAMELESS_U${user}_S${slot}_V6"
        val chainPreMangle = "NAMELESS_PRE_$key"
        val chainOutMangle = "NAMELESS_OUT_$key"
        val chainFilter = "NAMELESS_FILTER_$key"
        val chainV6Filter = "NAMELESS_V6_FILTER_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val start = user * 100000
        val end = start + 99999

        val tableId = 1000 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x20000 + (user * 0x100) + slot)

        val commands = mutableListOf<String>()

        // 1. Flush existing rules and stale socket caches
        commands.addAll(generateDisableCommands(user, slot))
        commands.add("ip route flush cache 2>/dev/null")
        commands.add("ndc resolver flushdefaultif 2>/dev/null")

        // 2. Policy Routing for TPROXY
        commands.add("ip rule add fwmark $markHex table $tableId pref 100")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref 100")
            commands.add("ip -6 route add local ::/0 dev lo table $tableId")
        }

        // Intercept marked UDP/TCP packets in PREROUTING to sing-box TPROXY
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 3. Mark Phone's Local Outbound UDP
        commands.add("iptables -t mangle -N $chainOutMangle")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j MARK --set-mark $markHex")

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

        if (settings.transportMode == TransportMode.TCP_AND_UDP) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }
        }
        // Force rule #1 in OUTPUT so Android netd cannot bypass it
        commands.add("iptables -t mangle -I OUTPUT 1 -m owner --uid-owner $start-$end -j $chainOutMangle")

        // 4. WebRTC Leak Shield (TCP Only Mode for Phone)
        commands.add("iptables -N $chainFilter 2>/dev/null")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")

        if (settings.transportMode == TransportMode.TCP_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -A $chainFilter -p udp -j DROP")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $uid -j DROP")
                }
            }
        }
        commands.add("iptables -I OUTPUT 1 -m owner --uid-owner $start-$end -j $chainFilter")

        // 5. Phone's Local TCP Redirection (NAT Table - Rule #1 in OUTPUT)
        commands.add("iptables -t nat -N $chainNatV4")
        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")

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
        // Insert as Rule #1 in nat OUTPUT to intercept Chrome before carrier bypass
        commands.add("iptables -t nat -I OUTPUT 1 -p tcp -m owner --uid-owner $start-$end -j $chainNatV4")

        // 6. IPv6 Leak Shield
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -N $chainV6Filter 2>/dev/null")
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A $chainV6Filter -j DROP")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $uid -j DROP")
                }
            }
            commands.add("ip6tables -I OUTPUT 1 -m owner --uid-owner $start-$end -j $chainV6Filter")
        } else {
            commands.add("ip6tables -t nat -N $chainNatV6")
            commands.add("ip6tables -t nat -A $chainNatV6 -m owner --uid-owner 0 -j RETURN")
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
            commands.add("ip6tables -t nat -A $chainNatV6 -d ::1/128 -j RETURN")
            commands.add("ip6tables -t nat -A $chainNatV6 -d fe80::/10 -j RETURN")

            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("ip6tables -t nat -I OUTPUT 1 -p tcp -m owner --uid-owner $start-$end -j $chainNatV6")
        }

        // 7. Hotspot & Cellular Tethering Engine
        if (settings.routeHotspot && user == 0) {
            commands.add("settings put global tether_offload_disabled 1 2>/dev/null")
            commands.add("setprop persist.sys.tether_offload 0 2>/dev/null")
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward")
            commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null")
            commands.add("echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null")
            commands.add("echo 0 > /proc/sys/net/ipv6/conf/all/forwarding 2>/dev/null")

            val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "bt-pan+")

            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block")

            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN")

            commands.add("iptables -t mangle -A $chainHotspotMangle -p tcp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")

            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host} -j RETURN")
            }

            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
            }
            commands.add("iptables -t mangle -A $chainHotspotMangle -s 192.168.0.0/16 -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
            commands.add("iptables -t mangle -A $chainHotspotMangle -s 172.16.0.0/12 -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")

            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
                }
                commands.add("iptables -t mangle -A $chainHotspotMangle -s 192.168.0.0/16 -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
                commands.add("iptables -t mangle -A $chainHotspotMangle -s 172.16.0.0/12 -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
            }

            commands.add("iptables -t mangle -I PREROUTING 2 -j $chainHotspotMangle")
        }

        return commands
    }

    fun generateDisableCommands(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): List<String> {
        val key = "u${user}_s$slot"
        val chainNatV4 = "NAMELESS_U${user}_S$slot"
        val chainNatV6 = "NAMELESS_U${user}_S${slot}_V6"
        val chainPreMangle = "NAMELESS_PRE_$key"
        val chainOutMangle = "NAMELESS_OUT_$key"
        val chainFilter = "NAMELESS_FILTER_$key"
        val chainV6Filter = "NAMELESS_V6_FILTER_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val start = user * 100000
        val end = start + 99999

        val tableId = 1000 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x20000 + (user * 0x100) + slot)

        return listOf(
            "ip6tables -D FORWARD -j $chainHotspotV6Block 2>/dev/null",
            "ip6tables -F $chainHotspotV6Block 2>/dev/null",
            "ip6tables -X $chainHotspotV6Block 2>/dev/null",

            "iptables -t mangle -D PREROUTING -j $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -F $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -X $chainHotspotMangle 2>/dev/null",

            "iptables -D OUTPUT -m owner --uid-owner $start-$end -j $chainFilter 2>/dev/null",
            "iptables -F $chainFilter 2>/dev/null",
            "iptables -X $chainFilter 2>/dev/null",

            "iptables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV4 2>/dev/null",
            "iptables -t nat -F $chainNatV4 2>/dev/null",
            "iptables -t nat -X $chainNatV4 2>/dev/null",

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

            "ip6tables -D OUTPUT -m owner --uid-owner $start-$end -j $chainV6Filter 2>/dev/null",
            "ip6tables -F $chainV6Filter 2>/dev/null",
            "ip6tables -X $chainV6Filter 2>/dev/null",

            "ip6tables -t nat -D OUTPUT -p tcp -m owner --uid-owner $start-$end -j $chainNatV6 2>/dev/null",
            "ip6tables -t nat -F $chainNatV6 2>/dev/null",
            "ip6tables -t nat -X $chainNatV6 2>/dev/null"
        )
    }
}
