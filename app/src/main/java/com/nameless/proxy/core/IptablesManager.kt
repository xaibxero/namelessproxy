package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})(/\d{1,2})?$""")
    private val IPV6_REGEX = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")

    private fun isValidIpv4(host: String): Boolean = IPV4_REGEX.matches(host.trim())

    private fun isValidIpv6(host: String): Boolean {
        val cleaned = ConfigGenerator.cleanHost(host)
        return cleaned.contains(":") && IPV6_REGEX.matches(cleaned)
    }

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val key = "u" + user + "_s" + slot

        val socksPort = inboundPort + 1
        val dnsPort = inboundPort + 2
        val tproxyPort = inboundPort + 8

        val chainNatV4 = "NAMELESS_U" + user + "_S" + slot
        val chainNatV6 = "NAMELESS_U" + user + "_S" + slot + "_V6"
        val chainPreMangle = "NAMELESS_PRE_" + key
        val chainOutMangle = "NAMELESS_OUT_" + key
        val chainPreMangleV6 = "NAMELESS_PRE6_" + key
        val chainOutMangleV6 = "NAMELESS_OUT6_" + key
        val chainFilter = "NAMELESS_FILTER_" + key
        val chainV6Filter = "NAMELESS_V6_FILTER_" + key
        val chainHotspotNat = "NAMELESS_HS_NAT_" + key
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_" + key
        val chainHotspotFwd = "NAMELESS_HS_FWD_" + key
        val chainHotspotV6Block = "NAMELESS_HS_V6_" + key

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tableId = ProfileManager.getTableId(user, slot)
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)

        // Hotspot/tether interfaces (wlan0 is upstream WAN, not a client)
        val tetherInterfaces = listOf("ap+", "swlan+", "softap+", "rndis+", "usb+", "p2p+", "ncm+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )
        val reservedV6 = listOf("::1/128", "fe80::/10", "ff00::/8")

        val supportsUdp = settings.transportMode == TransportMode.TCP_AND_UDP &&
            settings.type != ProxyType.SOCKS4 &&
            settings.type != ProxyType.HTTP

        val cleanedHost = ConfigGenerator.cleanHost(settings.host)
        val hostIsIpv4 = isValidIpv4(cleanedHost)
        val hostIsIpv6 = isValidIpv6(cleanedHost)

        val uidRanges = if (selectedUids.isNullOrEmpty()) {
            listOf("$appStart-$appEnd", "$isolatedStart-$isolatedEnd")
        } else {
            selectedUids.map { it.toString() } + "$isolatedStart-$isolatedEnd"
        }

        val commands = mutableListOf<String>()

        // 1. Clean existing rules
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Kernel tuning: Disable rp_filter across ALL interfaces so TPROXY works on cellular and Wi-Fi
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")

        // In IPv4_ONLY mode, disable IPv6 in sysctl to stop all cellular/Wi-Fi IPv6 DNS & WebRTC leaks
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true")
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true")
            commands.add("for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 1 > \$f 2>/dev/null || true; done")
        }

        // 3. Policy Routing for TPROXY
        val rulePref = 18000 + (user * 10) + slot
        commands.add("ip rule add fwmark $markHex table $tableId pref $rulePref")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref $rulePref 2>/dev/null || true")
            commands.add("ip -6 route add local ::/0 dev lo table $tableId 2>/dev/null || true")
        }

        // Ingress TPROXY Mangle for Phone
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 4. PHONE OUTBOUND MANGLE (Marks outgoing UDP for TPROXY)
        commands.add("iptables -t mangle -N $chainOutMangle")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $cleanedHost -j RETURN")
        }
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport $dnsPort -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
        }

        if (supportsUdp) {
            for (uRange in uidRanges) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uRange -j MARK --set-mark $markHex")
            }
        }
        commands.add("iptables -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // 5. PHONE FILTER SHIELD (Anti-Leak, STUN Block, WebRTC Leak Block, Private DNS DoT Block)
        commands.add("iptables -N $chainFilter 2>/dev/null")
        commands.add("iptables -A $chainFilter -o lo -j RETURN")
        commands.add("iptables -A $chainFilter -d 127.0.0.0/8 -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $inboundPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $socksPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $tproxyPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")

        if (hostIsIpv4) {
            commands.add("iptables -A $chainFilter -d $cleanedHost -j RETURN")
        }
        if (supportsUdp) {
            commands.add("iptables -A $chainFilter -p udp -m mark --mark $markHex -j RETURN")
        }

        // Allow essential system DHCP & NTP
        commands.add("iptables -A $chainFilter -p udp --dport 67:68 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 123 -j RETURN")

        // Hard Block Private DNS (DoT Port 853) to prevent leaks and force fallback to hijacked Port 53
        commands.add("iptables -A $chainFilter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 853 -j DROP")
        commands.add("iptables -A $chainFilter -p udp --dport 853 -j DROP")

        // Hard Block WebRTC / STUN / TURN Ports on both TCP and UDP across all processes
        commands.add("iptables -A $chainFilter -p udp --dport 3478:3497 -j DROP")
        commands.add("iptables -A $chainFilter -p udp --dport 5349:5350 -j DROP")
        commands.add("iptables -A $chainFilter -p udp --dport 19302:19309 -j DROP")
        commands.add("iptables -A $chainFilter -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 3478:3497 -j DROP")
        commands.add("iptables -A $chainFilter -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 5349:5350 -j DROP")
        commands.add("iptables -A $chainFilter -p tcp --dport 19302:19309 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 19302:19309 -j DROP")

        commands.add("iptables -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")

        // Kill-switch for target apps (Drops any unproxied traffic)
        for (uRange in uidRanges) {
            commands.add("iptables -A $chainFilter -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -A $chainFilter -m owner --uid-owner $uRange -j DROP")
        }
        commands.add("iptables -I OUTPUT 1 -j $chainFilter")

        // 6. PHONE NAT OUTPUT (Complete DNS Hijacking + TCP Redirect)
        commands.add("iptables -t nat -N $chainNatV4")
        if (hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $cleanedHost -j RETURN")
        }
        // UID 0 (sing-box daemon) must NEVER be redirected back to local DNS port
        commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 853 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 3478:3497 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 5349:5350 -j RETURN")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 19302:19309 -j RETURN")

        // Redirect ALL DNS (UDP & TCP 53) from ALL apps and system netd into sing-box
        commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")

        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        for (uRange in uidRanges) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort")
        }

        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
        }

        for (uRange in uidRanges) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort")
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 7. IPV6 RULES (Total IPv6 Leak Shield)
        commands.add("ip6tables -N $chainV6Filter 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 19302:19309 -j DROP 2>/dev/null || true")
        // If IPv4 only, drop 100% of non-loopback outbound IPv6 packets (eliminates all carrier IPv6 leaks)
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -A $chainV6Filter -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -I OUTPUT 1 -j $chainV6Filter 2>/dev/null || true")

        // 8. HOTSPOT ROUTING & LEAK PREVENTION
        if (settings.routeHotspot && user == 0) {
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 18500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")

            // Block IPv6 on tether interfaces to prevent IPv6 bypass/leak
            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP 2>/dev/null || true")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block 2>/dev/null || true")

            // Hotspot NAT: Intercept DNS 53 and TCP into sing-box
            commands.add("iptables -t nat -N $chainHotspotNat")
            if (hostIsIpv4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d $cleanedHost -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p udp --dport 67:68 -j RETURN")
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $inboundPort")
            }
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d $range -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -t nat -I PREROUTING 1 -j $chainHotspotNat")

            // Hotspot Mangle for TPROXY
            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp --dport 67:68 -j RETURN")
            }
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j RETURN")
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (hostIsIpv4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $cleanedHost -j RETURN")
            }
            if (supportsUdp) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
            }
            commands.add("iptables -t mangle -I PREROUTING 1 -j $chainHotspotMangle")

            // Hotspot FORWARD chain Leak-Shield
            commands.add("iptables -N $chainHotspotFwd 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 67:68 -j ACCEPT")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --sport 67:68 -j ACCEPT")
                // Block DoT & STUN leaks on connected hotspot clients
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp --dport 853 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 853 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 3478:3497 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 5349:5350 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 19302:19309 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp --dport 3478:3497 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp --dport 5349:5350 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp --dport 19302:19309 -j DROP")
                if (!supportsUdp) {
                    commands.add("iptables -A $chainHotspotFwd -i $iface -p udp ! --dport 53 -j DROP")
                }
            }
            commands.add("iptables -I FORWARD 1 -j $chainHotspotFwd")
        }

        // 9. Flush DNS caches safely
        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return commands
    }

    fun generateDisableCommands(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): List<String> {
        val key = "u" + user + "_s" + slot
        val chainNatV4 = "NAMELESS_U" + user + "_S" + slot
        val chainNatV6 = "NAMELESS_U" + user + "_S" + slot + "_V6"
        val chainPreMangle = "NAMELESS_PRE_" + key
        val chainOutMangle = "NAMELESS_OUT_" + key
        val chainPreMangleV6 = "NAMELESS_PRE6_" + key
        val chainOutMangleV6 = "NAMELESS_OUT6_" + key
        val chainFilter = "NAMELESS_FILTER_" + key
        val chainV6Filter = "NAMELESS_V6_FILTER_" + key
        val chainHotspotNat = "NAMELESS_HS_NAT_" + key
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_" + key
        val chainHotspotFwd = "NAMELESS_HS_FWD_" + key
        val chainHotspotV6Block = "NAMELESS_HS_V6_" + key

        val tableId = ProfileManager.getTableId(user, slot)
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)

        return listOf(
            // Re-enable IPv6 when tunnel is disconnected
            "echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true",
            "for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 0 > \$f 2>/dev/null || true; done",
            "iptables -D FORWARD -j $chainHotspotFwd 2>/dev/null",
            "iptables -F $chainHotspotFwd 2>/dev/null",
            "iptables -X $chainHotspotFwd 2>/dev/null",
            "ip6tables -D FORWARD -j $chainHotspotV6Block 2>/dev/null",
            "ip6tables -F $chainHotspotV6Block 2>/dev/null",
            "ip6tables -X $chainHotspotV6Block 2>/dev/null",
            "iptables -t nat -D PREROUTING -j $chainHotspotNat 2>/dev/null",
            "iptables -t nat -F $chainHotspotNat 2>/dev/null",
            "iptables -t nat -X $chainHotspotNat 2>/dev/null",
            "iptables -t mangle -D PREROUTING -j $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -F $chainHotspotMangle 2>/dev/null",
            "iptables -t mangle -X $chainHotspotMangle 2>/dev/null",
            "iptables -D OUTPUT -j $chainFilter 2>/dev/null",
            "iptables -F $chainFilter 2>/dev/null",
            "iptables -X $chainFilter 2>/dev/null",
            "ip6tables -D OUTPUT -j $chainV6Filter 2>/dev/null",
            "ip6tables -F $chainV6Filter 2>/dev/null",
            "ip6tables -X $chainV6Filter 2>/dev/null",
            "iptables -t nat -D OUTPUT -j $chainNatV4 2>/dev/null",
            "iptables -t nat -F $chainNatV4 2>/dev/null",
            "iptables -t nat -X $chainNatV4 2>/dev/null",
            "iptables -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null",
            "iptables -t mangle -F $chainPreMangle 2>/dev/null",
            "iptables -t mangle -X $chainPreMangle 2>/dev/null",
            "iptables -t mangle -D OUTPUT -j $chainOutMangle 2>/dev/null",
            "iptables -t mangle -F $chainOutMangle 2>/dev/null",
            "iptables -t mangle -X $chainOutMangle 2>/dev/null",
            "ip6tables -t mangle -D PREROUTING -j $chainPreMangleV6 2>/dev/null",
            "ip6tables -t mangle -F $chainPreMangleV6 2>/dev/null",
            "ip6tables -t mangle -X $chainPreMangleV6 2>/dev/null",
            "ip6tables -t mangle -D OUTPUT -j $chainOutMangleV6 2>/dev/null",
            "ip6tables -t mangle -F $chainOutMangleV6 2>/dev/null",
            "ip6tables -t mangle -X $chainOutMangleV6 2>/dev/null",
            "ip rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip route flush table $tableId 2>/dev/null",
            "ip -6 rule del fwmark $markHex table $tableId 2>/dev/null",
            "ip -6 route flush table $tableId 2>/dev/null",
            "ip rule del fwmark $hsMarkHex table $hsTableId 2>/dev/null",
            "ip route flush table $hsTableId 2>/dev/null",
            "ip6tables -t nat -D OUTPUT -j $chainNatV6 2>/dev/null",
            "ip6tables -t nat -F $chainNatV6 2>/dev/null",
            "ip6tables -t nat -X $chainNatV6 2>/dev/null"
        )
    }
}
