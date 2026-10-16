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
        val key = "u${user}_s$slot"
        val shVarF = "${'$'}f"

        val socksPort = inboundPort + 1
        val dnsPort = inboundPort + 2
        val tproxyPort = inboundPort + 8

        val chainNatV4 = "NAMELESS_U${user}_S$slot"
        val chainNatV6 = "NAMELESS_U${user}_S${slot}_V6"
        val chainPreMangle = "NAMELESS_PRE_$key"
        val chainOutMangle = "NAMELESS_OUT_$key"
        val chainPreMangleV6 = "NAMELESS_PRE6_$key"
        val chainOutMangleV6 = "NAMELESS_OUT6_$key"
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

        // 198.18.0.0/15 (FakeIP) is intentionally NOT in reservedV4 so FakeIP traffic is always redirected into sing-box
        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )
        // fc00::/18 (FakeIPv6) is intentionally NOT in reservedV6 so FakeIPv6 traffic is redirected into sing-box
        val reservedV6 = listOf("::1/128", "fe80::/10", "ff00::/8")

        val supportsUdp = settings.transportMode == TransportMode.TCP_AND_UDP &&
            settings.type != ProxyType.SOCKS5 &&
            settings.type != ProxyType.SOCKS4 &&
            settings.type != ProxyType.HTTP

        val blockQuicUdp = settings.transportMode == TransportMode.TCP_ONLY ||
            settings.type == ProxyType.SOCKS5 ||
            settings.type == ProxyType.SOCKS4 ||
            settings.type == ProxyType.HTTP

        val cleanedHost = ConfigGenerator.cleanHost(settings.host)
        val hostIsIpv4 = isValidIpv4(cleanedHost)
        val hostIsIpv6 = isValidIpv6(cleanedHost)

        val uidRanges = if (selectedUids.isNullOrEmpty()) {
            listOf("$appStart-$appEnd", "$isolatedStart-$isolatedEnd")
        } else {
            selectedUids.map { it.toString() } + "$isolatedStart-$isolatedEnd"
        }

        val commands = mutableListOf<String>()

        // 1. Clean any existing chains for this user & slot
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Kernel interface tuning for Cellular (rmnet_data+, v4-rmnet_data+, ccmni+) and Wi-Fi
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > $shVarF 2>/dev/null; done")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > $shVarF 2>/dev/null; done")

        // 3. Policy Routing for TPROXY (Use pref 18000+ so Android netd 464XLAT rules at 9000-16000 stay intact)
        val rulePref = 18000 + (user * 10) + slot
        commands.add("ip rule add fwmark $markHex table $tableId pref $rulePref")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")

        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref $rulePref")
            commands.add("ip -6 route add local ::/0 dev lo table $tableId")
        }

        // Ingress TPROXY Mangle for Phone (IPv4)
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 4. PHONE OUTBOUND MANGLE (IPv4 UDP TPROXY)
        commands.add("iptables -t mangle -N $chainOutMangle")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $cleanedHost -j RETURN")
        }
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 3478:3497 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 5349:5350 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 19302:19309 -j RETURN")
        if (blockQuicUdp) {
            commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 443 -j RETURN")
            commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 80 -j RETURN")
        }

        for (range in reservedV4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
        }

        if (supportsUdp) {
            for (uRange in uidRanges) {
                commands.add("iptables -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uRange -j MARK --set-mark $markHex")
            }
        }
        commands.add("iptables -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // 5. PHONE FILTER SHIELD
        // CRITICAL FOR 464XLAT (clatd UID 1029) ON JAZZ LTE:
        // Because clatd translates IPv4 packets under UID 1029 (which is inside 0-9999),
        // we MUST drop WebRTC STUN ports and un-tunneled UDP BEFORE `--uid-owner 0-9999 -j RETURN`!
        commands.add("iptables -N \$chainFilter 2>/dev/null")
        commands.add("iptables -A \$chainFilter -o lo -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport$inboundPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport$socksPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport$dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport$dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport$tproxyPort -j RETURN")

        // Allow sing-box outbound to proxy server (in case Hysteria2 / QUIC uses UDP to cleanedHost)
        if (hostIsIpv4) {
            commands.add("iptables -A $chainFilter -d$cleanedHost -j RETURN")
        }
        // Allow sing-box (UID 0) bootstrap DNS to 9.9.9.9:53 and DHCP (67:68) / local LAN broadcast
        commands.add("iptables -A $chainFilter -p udp -d${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -A \$chainFilter -p udp --dport 67:68 -j RETURN")
        for (range in reservedV4) {
            commands.add("iptables -A $chainFilter -d$range -j RETURN")
        }

        // Globally block WebRTC STUN/TURN ports & DoT 853 BEFORE allowing UID 0-9999 (blocks clatd UID 1029 leaks!)
        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("iptables -A $chainFilter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null \vert{}\vert{} iptables -A$chainFilter -p tcp --dport 853 -j DROP")
            commands.add("iptables -A \$chainFilter -p udp --dport 853 -j DROP")
            commands.add("iptables -A \$chainFilter -p udp --dport 3478:3497 -j DROP")
            commands.add("iptables -A \$chainFilter -p udp --dport 5349:5350 -j DROP")
            commands.add("iptables -A \$chainFilter -p udp --dport 19302:19309 -j DROP")
            if (supportsUdp) {
                commands.add("iptables -A $chainFilter -p udp -m mark --mark$markHex -j RETURN")
            }
            // Drop all other direct outbound UDP so neither apps nor clatd (UID 1029) can leak WebRTC UDP!
            commands.add("iptables -A \$chainFilter -p udp -j DROP")
        }

        commands.add("iptables -A \$chainFilter -m owner --uid-owner 0-9999 -j RETURN")

        for (uRange in uidRanges) {
            commands.add("iptables -A $chainFilter -p tcp --dport 853 -m owner --uid-owner$uRange -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp --dport 853 -m owner --uid-owner$uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 853 -m owner --uid-owner$uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 3478:3497 -m owner --uid-owner$uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 5349:5350 -m owner --uid-owner$uRange -j DROP")
            commands.add("iptables -A $chainFilter -p udp --dport 19302:19309 -m owner --uid-owner$uRange -j DROP")

            if (blockQuicUdp) {
                commands.add("iptables -A $chainFilter -p udp --dport 443 -m owner --uid-owner$uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainFilter -p udp --dport 443 -m owner --uid-owner$uRange -j DROP")
                commands.add("iptables -A $chainFilter -p udp --dport 80 -m owner --uid-owner$uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -A $chainFilter -p udp --dport 80 -m owner --uid-owner$uRange -j DROP")
            }

            if (supportsUdp) {
                commands.add("iptables -A $chainFilter -p udp -m mark --mark$markHex -j RETURN")
            }

            commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner$uRange -j DROP")
        }
        commands.add("iptables -I OUTPUT 1 -j \$chainFilter")

        // 6. PHONE NAT OUTPUT (Redirect ALL TCP + DNS 53 into sing-box)
        commands.add("iptables -t nat -N \$chainNatV4")
        if (hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d$cleanedHost -j RETURN")
        }
        commands.add("iptables -t nat -A $chainNatV4 -p udp -d${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")

        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -j REDIRECT --to-ports$dnsPort")
            commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports$dnsPort")
        } else {
            for (uRange in uidRanges) {
                commands.add("iptables -t nat -A \$chainNatV4 -p udp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports$dnsPort")
                commands.add("iptables -t nat -A \$chainNatV4 -p tcp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports$dnsPort")
            }
        }

        commands.add("iptables -t nat -A \$chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d$range -j RETURN")
        }

        // Redirect ALL TCP ports (including 3478/19302 TCP STUN & FakeIP 198.18.0.0/15) into sing-box redirect-in
        // (sing-box's route rules immediately reject STUN and DoH while tunneling all web traffic!)
        for (uRange in uidRanges) {
            commands.add("iptables -t nat -A \$chainNatV4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports$inboundPort")
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j \$chainNatV4")

        // 7. PHONE IPV6 HANDLING (Blocks clatd / netd IPv6 UDP STUN & DNS leaks on Jazz LTE!)
        if (settings.ipMode != IpMode.IPV4_ONLY && supportsUdp) {
            commands.add("ip6tables -t mangle -N \$chainPreMangleV6 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A $chainPreMangleV6 -p tcp -m mark --mark$markHex -j TPROXY --on-port $tproxyPort --tproxy-mark$markHex 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A $chainPreMangleV6 -p udp -m mark --mark$markHex -j TPROXY --on-port $tproxyPort --tproxy-mark$markHex 2>/dev/null || true")
            commands.add("ip6tables -t mangle -I PREROUTING 1 -j \$chainPreMangleV6 2>/dev/null || true")

            commands.add("ip6tables -t mangle -N \$chainOutMangleV6 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
            if (hostIsIpv6) {
                commands.add("ip6tables -t mangle -A $chainOutMangleV6 -d$cleanedHost -j RETURN 2>/dev/null || true")
            }
            commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 53 -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A $chainOutMangleV6 -p udp --dport$dnsPort -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 3478:3497 -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 5349:5350 -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 19302:19309 -j RETURN 2>/dev/null || true")
            if (blockQuicUdp) {
                commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 443 -j RETURN 2>/dev/null || true")
                commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp --dport 80 -j RETURN 2>/dev/null || true")
            }
            for (r6 in reservedV6) {
                commands.add("ip6tables -t mangle -A $chainOutMangleV6 -d$r6 -j RETURN 2>/dev/null || true")
            }
            for (uRange in uidRanges) {
                commands.add("ip6tables -t mangle -A \$chainOutMangleV6 -p udp -m owner --uid-owner $uRange -j MARK --set-mark$markHex 2>/dev/null || true")
            }
            commands.add("ip6tables -t mangle -I OUTPUT 1 -j \$chainOutMangleV6 2>/dev/null || true")
        }

        commands.add("ip6tables -t nat -N \$chainNatV6 2>/dev/null || true")
        if (hostIsIpv6) {
            commands.add("ip6tables -t nat -A $chainNatV6 -d$cleanedHost -j RETURN 2>/dev/null || true")
        }
        commands.add("ip6tables -t nat -A $chainNatV6 -p udp -d${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        for (r6 in reservedV6) {
            commands.add("ip6tables -t nat -A $chainNatV6 -d$r6 -j RETURN 2>/dev/null || true")
        }

        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -j REDIRECT --to-ports$dnsPort 2>/dev/null || true")
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports$dnsPort 2>/dev/null || true")
        } else {
            for (uRange in uidRanges) {
                commands.add("ip6tables -t nat -A \$chainNatV6 -p udp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports$dnsPort 2>/dev/null || true")
                commands.add("ip6tables -t nat -A \$chainNatV6 -p tcp --dport 53 -m owner --uid-owner $uRange -j REDIRECT --to-ports$dnsPort 2>/dev/null || true")
            }
        }

        commands.add("ip6tables -t nat -A \$chainNatV6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
        for (uRange in uidRanges) {
            commands.add("ip6tables -t nat -A \$chainNatV6 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports$inboundPort 2>/dev/null || true")
        }
        commands.add("ip6tables -t nat -I OUTPUT 1 -j \$chainNatV6 2>/dev/null || true")

        // IPv6 Filter Shield:
        // CRITICAL: Drop all IPv6 UDP (including clatd UID 1029 NAT64-translated UDP STUN packets!)
        // BEFORE `--uid-owner 0-9999 -j RETURN` so Jazz LTE NAT64 (154.80.124.65) can NEVER leak in WebRTC!
        commands.add("ip6tables -N \$chainV6Filter 2>/dev/null || true")
        commands.add("ip6tables -A \$chainV6Filter -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A \$chainV6Filter -p ipv6-icmp -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport$dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport$dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport$inboundPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport$socksPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p udp --dport$tproxyPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A \$chainV6Filter -p udp --dport 546:547 -j RETURN 2>/dev/null || true")
        for (r6 in reservedV6) {
            commands.add("ip6tables -A $chainV6Filter -d$r6 -j RETURN 2>/dev/null || true")
        }
        if (hostIsIpv6) {
            commands.add("ip6tables -A $chainV6Filter -d$cleanedHost -j RETURN 2>/dev/null || true")
        }
        commands.add("ip6tables -A $chainV6Filter -p udp -d${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -A $chainV6Filter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null \vert{}\vert{} ip6tables -A$chainV6Filter -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -A \$chainV6Filter -p udp --dport 853 -j DROP 2>/dev/null || true")

        if (user == 0 && selectedUids.isNullOrEmpty()) {
            commands.add("ip6tables -A $chainV6Filter -p udp --dport 53 -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null \vert{}\vert{} ip6tables -A$chainV6Filter -p udp --dport 53 -j DROP 2>/dev/null || true")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null \vert{}\vert{} ip6tables -A$chainV6Filter -p tcp --dport 53 -j DROP 2>/dev/null || true")
            if (supportsUdp && settings.ipMode != IpMode.IPV4_ONLY) {
                commands.add("ip6tables -A $chainV6Filter -p udp -m mark --mark$markHex -j RETURN 2>/dev/null || true")
            }
            // Block all other IPv6 UDP BEFORE UID 0-9999 so clatd (UID 1029) never translates WebRTC UDP to NAT64!
            commands.add("ip6tables -A \$chainV6Filter -p udp -j DROP 2>/dev/null || true")
        }

        // Allow root/system (UID 0-9999, including clatd TCP translation!) for TCP/ICMPv6
        commands.add("ip6tables -A \$chainV6Filter -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        for (uRange in uidRanges) {
            commands.add("ip6tables -A $chainV6Filter -p tcp -m owner --uid-owner$uRange -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp -m owner --uid-owner$uRange -j DROP 2>/dev/null || true")
            commands.add("ip6tables -A $chainV6Filter -p udp -m owner --uid-owner$uRange -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -A $chainV6Filter -p udp -m owner --uid-owner$uRange -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -I OUTPUT 1 -j \$chainV6Filter 2>/dev/null || true")

        // 8. ZERO-LEAK HOTSPOT / TETHERING ROUTING
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            commands.add("ip rule add fwmark $hsMarkHex table$hsTableId pref 18500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table \$hsTableId")

            commands.add("iptables -I FORWARD 1 -j ACCEPT 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null")

            commands.add("ip6tables -N \$chainHotspotV6Block 2>/dev
