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

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tableId = 2080 + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "bt-pan+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val commands = mutableListOf<String>()

        // 1. Flush existing rules
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Safe Loopback Settings
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/lo/rp_filter 2>/dev/null")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null")

        // 3. Policy Routing for TPROXY
        commands.add("ip rule add fwmark $markHex table $tableId pref 100")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref 100")
            commands.add("ip -6 route add local ::/0 dev lo table $tableId")
        }

        // Ingress TPROXY Mangle
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 4. OUTBOUND MANGLE: Fixes 4G Flapping
        // Exempt UIDs 0-9999 (RIL modem, Radio, NetworkMonitor, Root) FIRST so carrier signals are never touched
        commands.add("iptables -t mangle -N $chainOutMangle")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
        }
        if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
            commands.add("iptables -t mangle -A $chainOutMangle -d ${settings.host} -j RETURN")
        }

        if (settings.transportMode == TransportMode.TCP_AND_UDP) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $appStart-$appEnd -j MARK --set-mark $markHex")
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
            }
        }
        commands.add("iptables -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // 5. WEBRTC SHIELD: Preserves Locked WebRTC
        commands.add("iptables -N $chainFilter 2>/dev/null")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")
        commands.add("iptables -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 3478 -j DROP")

        if (settings.transportMode == TransportMode.TCP_ONLY) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $appStart-$appEnd -j DROP")
                commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $uid -j DROP")
                }
            }
        }
        commands.add("iptables -I OUTPUT 1 -j $chainFilter")

        // 6. LOCAL TCP & DNS REDIRECTION: Preserves Locked DNS
        commands.add("iptables -t nat -N $chainNatV4")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
        }
        if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host} -j RETURN")
        }

        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $appStart-$appEnd -j REDIRECT --to-ports $inboundPort")
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort")
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
            }
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 7. IPV6 LEAK SHIELD
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -N $chainV6Filter 2>/dev/null")
            commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner 0-9999 -j RETURN")
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $appStart-$appEnd -j DROP")
                commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $uid -j DROP")
                }
            }
            commands.add("ip6tables -I OUTPUT 1 -j $chainV6Filter")
        } else {
            commands.add("ip6tables -t nat -N $chainNatV6 2>/dev/null")
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
            commands.add("ip6tables -t nat -A $chainNatV6 -m owner --uid-owner 0-9999 -j RETURN")
            commands.add("ip6tables -t nat -A $chainNatV6 -d ::1/128 -j RETURN")
            commands.add("ip6tables -t nat -A $chainNatV6 -d fe80::/10 -j RETURN")

            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $appStart-$appEnd -j REDIRECT --to-ports $inboundPort")
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
                }
            }
            commands.add("ip6tables -t nat -I OUTPUT 1 -j $chainNatV6")
        }

        // 8. HOTSPOT ROUTING & FORWARDING: Fixes Laptop Internet Access
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")

            // Policy routing for tethered clients
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")

            // Unblock Android's internal FORWARD chain for tethering
            commands.add("iptables -I FORWARD 1 -j ACCEPT 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null")

            // Block IPv6 leakage on hotspot interfaces
            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block")

            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN")

            // INTERCEPT DNS FIRST so the laptop's DNS requests are tunneled through the proxy
            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p tcp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            }

            // Exclude local router subnet traffic
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host} -j RETURN")
            }

            // Route all laptop external TCP connections to the proxy
            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            }

            // Route all laptop external UDP connections to the proxy if TCP+UDP mode is active
            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
            }

            commands.add("iptables -t mangle -I PREROUTING 1 -j $chainHotspotMangle")
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

        val tableId = 2080 + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        return listOf(
            "iptables -D FORWARD -j ACCEPT 2>/dev/null",
            "iptables -t nat -D POSTROUTING -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null",
            "iptables -t nat -D POSTROUTING -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null",

            "ip6tables -D FORWARD -j $chainHotspotV6Block 2>/dev/null",
            "ip6tables -F $chainHotspotV6Block 2>/dev/null",
            "ip6tables -X $chainHotspotV6Block 2>/dev/null",

            "iptables -t mangle -D PREROUTING -j $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -F $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -X $chainHotspotMangle 2>/dev/null",

            "iptables -D OUTPUT -j $chainFilter 2>/dev/null",
            "iptables -F $chainFilter 2>/dev/null",
            "iptables -X $chainFilter 2>/dev/null",

            "iptables -t nat -D OUTPUT -j $chainNatV4 2>/dev/null",
            "iptables -t nat -F $chainNatV4 2>/dev/null",
            "iptables -t nat -X $chainNatV4 2>/dev/null",

            "iptables -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null",
            "iptables -t mangle -F $chainPreMangle 2>/dev/null",
            "iptables -t mangle -X $chainPreMangle 2>/dev/null",

            "iptables -t mangle -D OUTPUT -j $chainOutMangle 2>/dev/null",
            "iptables -t mangle -F $chainOutMangle 2>/dev/null",
            "iptables -t mangle -X $chainOutMangle 2>/dev/null",

            "ip rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip route flush table $tableId 2>/dev/null",
            "ip -6 rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip -6 route flush table $tableId 2>/dev/null",

            "ip rule del fwmark $hsMarkHex table $hsTableId 2>/dev/null",
            "ip route flush table $hsTableId 2>/dev/null",

            "ip6tables -D OUTPUT -j $chainV6Filter 2>/dev/null",
            "ip6tables -F $chainV6Filter 2>/dev/null",
            "ip6tables -X $chainV6Filter 2>/dev/null",

            "ip6tables -t nat -D OUTPUT -j $chainNatV6 2>/dev/null",
            "ip6tables -t nat -F $chainNatV6 2>/dev/null",
            "ip6tables -t nat -X $chainNatV6 2>/dev/null"
        )
    }
}
