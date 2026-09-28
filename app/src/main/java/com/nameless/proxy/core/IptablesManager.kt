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
        val chainFilter = "NAMELESS_FILTER_$key"
        val chainV6Filter = "NAMELESS_V6_FILTER_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        // Strictly target user-installed applications (UIDs 10000-99999)
        // Never touch Android System, Telephony RIL, NetworkStack, or Radio (UIDs 0-9999)
        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        // Dedicated policy routing table for Hotspot/Tether clients ONLY
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        val commands = mutableListOf<String>()

        // 1. Flush stale rules from this slot
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Safe Loopback Settings (Never touch cellular rmnet* interfaces)
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/lo/rp_filter 2>/dev/null")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null")

        // 3. Reserved private IP address ranges
        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        // 4. PHONE LOCAL TCP & DNS REDIRECTION (NAT Table)
        commands.add("iptables -t nat -N $chainNatV4")

        // Exclude direct connection to proxy server
        if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host} -j RETURN")
        }

        // DNS LEAK FIX:
        // Redirect TCP Port 53 to sing-box redirect inbound (hijack-dns resolves via proxy)
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
        // Forward UDP Port 53 directly to Cloudflare US DNS (1.1.1.1) to avoid leaking carrier DNS while keeping Android network probes alive
        commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -j DNAT --to-destination 1.1.1.1:53")

        // Never redirect system/telephony daemons (UIDs 0-9999) to keep RIL cellular radio stable
        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        // Bypass local / reserved IP subnets
        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
        }

        // Redirect user applications' TCP traffic into sing-box
        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $appStart-$appEnd -j REDIRECT --to-ports $inboundPort")
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort")
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
            }
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 5. WEBRTC & QUIC LEAK SHIELD
        commands.add("iptables -N $chainFilter 2>/dev/null")
        commands.add("iptables -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")

        // Drop UDP STUN on port 3478 so Chrome cannot expose the cellular IP (forces WebRTC over proxy TCP)
        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -A $chainFilter -p udp --dport 3478 -m owner --uid-owner $appStart-$appEnd -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 3478 -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -A $chainFilter -p udp --dport 3478 -m owner --uid-owner $uid -j DROP")
            }
        }

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

        // 6. IPV6 LEAK SHIELD
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

        // 7. HOTSPOT / TETHERING ENGINE (TPROXY restricted ONLY to ingress packets)
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")

            // Policy routing strictly isolated for Hotspot clients (never touches local phone sockets)
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")

            val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "bt-pan+")

            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block")

            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN")

            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (settings.host.isNotEmpty() && !settings.host.contains(":")) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host} -j RETURN")
            }

            // Always intercept DNS from connected hotspot clients
            commands.add("iptables -t mangle -A $chainHotspotMangle -p tcp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")

            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            }
            commands.add("iptables -t mangle -A $chainHotspotMangle -s 192.168.0.0/16 -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            commands.add("iptables -t mangle -A $chainHotspotMangle -s 172.16.0.0/12 -p tcp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")

            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
                commands.add("iptables -t mangle -A $chainHotspotMangle -s 192.168.0.0/16 -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                commands.add("iptables -t mangle -A $chainHotspotMangle -s 172.16.0.0/12 -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
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
        val chainFilter = "NAMELESS_FILTER_$key"
        val chainV6Filter = "NAMELESS_V6_FILTER_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        return listOf(
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

            "ip rule del fwmark $hsMarkHex table $hsTableId 2>/dev/null",
            "ip route flush table $hsTableId 2>/dev/null",

            "ip6tables -D OUTPUT -j $chainV6Filter 2>/dev/null",
            "ip6tables -F $chainV6Filter 2>/dev/null",
            "ip6tables -X $chainV6Filter 2>/dev/null",

            "ip6tables -t nat -D OUTPUT -j $chainNatV6 2>/dev/null",
            "ip6tables -F $chainNatV6 2>/dev/null",
            "ip6tables -X $chainNatV6 2>/dev/null"
        )
    }
}
