package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})(/\\d{1,2})?$")

    private fun isValidIpv4(host: String): Boolean = IPV4_REGEX.matches(host.trim())

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null
    ): List<String> {
        val user = ProfileManager.androidUserId
        val slot = ProfileManager.activeSlot
        val key = "u${user}_s$slot"

        val socksPort = inboundPort + 1
        val dnsPort = inboundPort + 2
        val tproxyPort = inboundPort + 8
        val bypassMarkHex = "0x" + Integer.toHexString(ConfigGenerator.SINGBOX_ROUTING_MARK)

        val chainNatV4 = "NAMELESS_U${user}_S$slot"
        val chainNatV6 = "NAMELESS_U${user}_S${slot}_V6"
        val chainPreMangle = "NAMELESS_PRE_$key"
        val chainOutMangle = "NAMELESS_OUT_$key"
        val chainFilter = "NAMELESS_FILTER_$key"
        val chainV6Filter = "NAMELESS_V6_FILTER_$key"
        val chainHotspotNat = "NAMELESS_HS_NAT_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotFwd = "NAMELESS_HS_FWD_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tableId = ProfileManager.getTableId(user, slot)
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)

        val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "swlan+", "wigig+", "p2p+", "ncm+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        // Protocols that should strictly use TCP and reject QUIC/UDP 443 for instant browser loading
        val isTcpCentric = settings.transportMode == TransportMode.TCP_ONLY ||
            settings.type == ProxyType.SOCKS5 ||
            settings.type == ProxyType.SOCKS4 ||
            settings.type == ProxyType.HTTP

        val hostIsIpv4 = isValidIpv4(settings.host)
        val commands = mutableListOf<String>()

        // 1. Clean any existing chains for this user & slot
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Kernel interface tuning for Cellular (rmnet_data+, v4-rmnet_data+, ccmni+) and Wi-Fi
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null; done")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null; done")

        // 3. Policy Routing for TPROXY
        commands.add("ip rule add fwmark $markHex table $tableId pref ${100 + user}")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref ${100 + user}")
            commands.add("ip -6 route add local ::/0 dev lo table $tableId")
        }

        // Ingress TPROXY Mangle for Phone
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 4. PHONE OUTBOUND MANGLE (Only for full UDP TPROXY protocols like VLESS / Hysteria2 / Shadowsocks)
        commands.add("iptables -t mangle -N $chainOutMangle")
        commands.add("iptables -t mangle -A $chainOutMangle -m mark --mark $bypassMarkHex -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d ${settings.host.trim()} -j RETURN")
        }
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
        }

        if (!isTcpCentric) {
            if (selectedUids.isNullOrEmpty()) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $appStart-$appEnd -j MARK --set-mark $markHex")
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j MARK --set-mark $markHex")
            } else {
                for (uid in selectedUids) {
                    commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uid -j MARK --set-mark $markHex")
                }
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j MARK --set-mark $markHex")
            }
        }
        commands.add("iptables -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // 5. PHONE FILTER SHIELD (Zero WebRTC Leaks, Private DNS DoT 853 Block, Instant QUIC Fallback)
        commands.add("iptables -N $chainFilter 2>/dev/null")
        // Allow sing-box's own marked sockets and redirected local ports
        commands.add("iptables -A $chainFilter -m mark --mark $bypassMarkHex -j RETURN")
        commands.add("iptables -A $chainFilter -o lo -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $inboundPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $socksPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $tproxyPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport 53 -j RETURN")

        // Block Private DNS (DoT Port 853) so Android netd immediately falls back to Port 53 (which we hijack into sing-box!)
        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("iptables -A $chainFilter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 853 -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainFilter -p udp --dport 853 -j DROP")
        }

        // Helper to apply per-UID filter rules without touching other Android user profiles
        val uidRanges = if (selectedUids.isNullOrEmpty()) {
            listOf("$appStart-$appEnd", "$isolatedStart-$isolatedEnd")
        } else {
            selectedUids.map { it.toString() } + "$isolatedStart-$isolatedEnd"
        }

        for (uRange in uidRanges) {
            // Block DoT 853 per-UID
            commands.add("iptables -A $chainFilter -p tcp --dport 853 -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 853 -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 853 -m owner --uid-owner $uRange -j DROP")
            // Block WebRTC STUN/TURN leak ports (3478, 5349, 19302)
            commands.add("iptables -A $chainFilter -p udp --dport 3478 -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 5349 -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 19302 -m owner --uid-owner $uRange -j DROP")

            if (isTcpCentric) {
                // Reject QUIC (UDP 443/80) with instant ICMP unreachable so Chrome/WebView switches to TCP 443 in 0ms
                commands.add("iptables -A $chainFilter -p udp --dport 443 -m owner --uid-owner $uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainFilter -p udp --dport 443 -m owner --uid-owner $uRange -j DROP")
                commands.add("iptables -A $chainFilter -p udp --dport 80 -m owner --uid-owner $uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainFilter -p udp --dport 80 -m owner --uid-owner $uRange -j DROP")
                // Drop all other non-DNS UDP packets in TCP-centric / Kill-Switch mode to guarantee zero WebRTC UDP leaks
                if (settings.transportMode == TransportMode.TCP_ONLY || settings.enableKillSwitch) {
                    commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $uRange -j DROP")
                }
            }
        }
        commands.add("iptables -I OUTPUT 1 -j $chainFilter")

        // 6. PHONE NAT OUTPUT (Redirect TCP + DNS 53 into sing-box using REDIRECT for Cellular rmnet compatibility)
        commands.add("iptables -t nat -N $chainNatV4")
        commands.add("iptables -t nat -A $chainNatV4 -m mark --mark $bypassMarkHex -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host.trim()} -j RETURN")
        }

        // If running in Owner Profile (User 0) with Whole-Profile mode, also intercept Android netd (UID 0/1052) DNS
        // (sing-box's own DNS packets carry bypassMarkHex 0x2026 so they already returned above!)
        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
            commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
        } else {
            // Strictly per-UID DNS interception (multi-user safe for User 10, 11, etc. or split-tunnel mode)
            for (uRange in uidRanges) {
                commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports $dnsPort")
            }
            // In secondary Android profiles (User 10+), netd still sends DNS on behalf of apps if Private DNS is off;
            // if User 0 is not running its own proxy, redirect netd port 53 to this active profile's dnsPort
            commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -m owner --uid-owner 0 -j REDIRECT --to-ports $dnsPort")
            commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -m owner --uid-owner 1052 -j REDIRECT --to-ports $dnsPort")
        }

        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
        }

        for (uRange in uidRanges) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort")
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 7. PHONE IPV6 HANDLING (Crucial for Cellular LTE/5G IPv6 & Dual-Stack APNs)
        commands.add("ip6tables -t nat -N $chainNatV6 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -m mark --mark $bypassMarkHex -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -d ::1/128 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -d fe80::/10 -j RETURN 2>/dev/null || true")

        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        } else {
            for (uRange in uidRanges) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
            }
            commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -m owner --uid-owner 0 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
            commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -m owner --uid-owner 1052 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        }

        // Redirect IPv6 TCP from target UIDs into sing-box (which listens on "::" and tunnels over IPv4 or IPv6 proxy!)
        commands.add("ip6tables -t nat -A $chainNatV6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
        for (uRange in uidRanges) {
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        }
        commands.add("ip6tables -t nat -I OUTPUT 1 -j $chainNatV6 2>/dev/null || true")

        // Block any un-redirected IPv6 UDP/DoT/WebRTC so IPv6 never leaks on Cellular LTE
        commands.add("ip6tables -N $chainV6Filter 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -m mark --mark $bypassMarkHex -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport 853 -j DROP 2>/dev/null || true")

        if (settings.ipMode == IpMode.IPV4_ONLY) {
            // Fallback if ip6tables nat is not compiled in the device kernel: reject direct IPv6 immediately so apps use IPv4
            for (uRange in uidRanges) {
                commands.add("ip6tables -A $chainV6Filter -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -A $chainV6Filter -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
                commands.add("ip6tables -A $chainV6Filter -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            }
        }
        commands.add("ip6tables -I OUTPUT 1 -j $chainV6Filter 2>/dev/null || true")

        // 8. ZERO-LEAK HOTSPOT / TETHERING ROUTING
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")

            commands.add("iptables -I FORWARD 1 -j ACCEPT 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null")

            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j REJECT 2>/dev/null || ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block")

            commands.add("iptables -t nat -N $chainHotspotNat")
            if (hostIsIpv4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d ${settings.host.trim()} -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
            }
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d $range -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -t nat -I PREROUTING 1 -j $chainHotspotNat")

            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j RETURN")
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (hostIsIpv4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host.trim()} -j RETURN")
            }
            if (!isTcpCentric) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
            }
            commands.add("iptables -t mangle -I PREROUTING 1 -j $chainHotspotMangle")

            // Block WebRTC STUN, DoT 853, and un-tunneled UDP/QUIC on Hotspot clients
            commands.add("iptables -N $chainHotspotFwd 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainHotspotFwd -i $iface -p tcp --dport 853 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 853 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 3478 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 5349 -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 19302 -j DROP")
                if (isTcpCentric) {
                    commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 443 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainHotspotFwd -i $iface -p udp --dport 443 -j DROP")
                    commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 80 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainHotspotFwd -i $iface -p udp --dport 80 -j DROP")
                    commands.add("iptables -A $chainHotspotFwd -i $iface -p udp ! --dport 53 -j DROP")
                }
            }
            commands.add("iptables -I FORWARD 1 -j $chainHotspotFwd")
        }

        // 9. Flush stale sockets so browsers immediately open fresh tunneled connections
        commands.add("ss -K dport = :443 2>/dev/null || true")
        commands.add("ss -K dport = :853 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

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
        val chainHotspotNat = "NAMELESS_HS_NAT_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotFwd = "NAMELESS_HS_FWD_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val tableId = ProfileManager.getTableId(user, slot)
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)

        return listOf(
            "iptables -D FORWARD -j $chainHotspotFwd 2>/dev/null",
            "iptables -F $chainHotspotFwd 2>/dev/null",
            "iptables -X $chainHotspotFwd 2>/dev/null",
            "iptables -D FORWARD -j ACCEPT 2>/dev/null",
            "iptables -t nat -D POSTROUTING -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null",
            "iptables -t nat -D POSTROUTING -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null",
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
