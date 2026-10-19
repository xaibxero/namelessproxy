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

        // Full user app + SDK sandbox (20000-29999) + isolated WebView/Chrome zygote (90000-99999) UID span
        val fullUserAppStart = user * 100000 + 10000
        val fullUserAppEnd = user * 100000 + 99999
        val sdkSandboxStart = user * 100000 + 20000
        val sdkSandboxEnd = user * 100000 + 29999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tableId = ProfileManager.getTableId(user, slot)
        val v6BlockTableId = ProfileManager.getV6BlockTableId(user, slot)
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)
        val singboxBypassMark = ProfileManager.SINGBOX_BYPASS_MARK_HEX

        // Hotspot/tether interfaces (wlan0 is upstream WAN, not a client)
        val tetherInterfaces = listOf("ap+", "swlan+", "softap+", "rndis+", "usb+", "p2p+", "ncm+", "wlan1", "wlan2")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.0.0.0/24", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )
        val reservedV6 = listOf("::1/128", "fe80::/10", "ff00::/8")

        val supportsUdp = settings.transportMode == TransportMode.TCP_AND_UDP &&
            !settings.strictWebRtcShield &&
            settings.type != ProxyType.SOCKS5 &&
            settings.type != ProxyType.SOCKS4 &&
            settings.type != ProxyType.HTTP

        val cleanedHost = ConfigGenerator.cleanHost(settings.host)
        val hostIsIpv4 = isValidIpv4(cleanedHost)
        val hostIsIpv6 = isValidIpv6(cleanedHost)

        val uidRanges = if (selectedUids.isNullOrEmpty()) {
            listOf("$fullUserAppStart-$fullUserAppEnd")
        } else {
            selectedUids.distinct().map { it.toString() } +
                listOf("$sdkSandboxStart-$sdkSandboxEnd", "$isolatedStart-$isolatedEnd")
        }

        val commands = mutableListOf<String>()

        // 1. Clean existing rules first
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Kernel tuning: Disable rp_filter across ALL cellular (rmnet/ccmni/v4-rmnet) & Wi-Fi (wlan) interfaces
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/default/route_localnet 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")

        // 3. Policy Routing for TPROXY & IPv6 App-Level Leak Block
        // Note: On 4G/5G cellular networks using IPv6-only APN + 464XLAT (clatd UID 1029 -> v4-rmnet_data+),
        // we must NOT kill raw interface IPv6 for clatd (UID 1029), or cellular data drops.
        // Instead, we use high-priority IPv6 policy routing (pref 8900) for all app UIDs so any app
        // IPv6 connect()/getsockname() (WebRTC local IP discovery) immediately returns ENETUNREACH on
        // both Cellular and Wi-Fi without disturbing clatd (UID 1029)!
        val rulePref = 18000 + (user * 10) + slot
        val v6BlockPref = 8900 + (user * 10) + slot
        commands.add("ip rule add fwmark $markHex table $tableId pref $rulePref 2>/dev/null || true")
        commands.add("ip route replace local 0.0.0.0/0 dev lo table $tableId 2>/dev/null || true")

        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip -6 route replace unreachable ::/0 table $v6BlockTableId 2>/dev/null || true")
            for (uRange in uidRanges) {
                commands.add("ip -6 rule add uidrange $uRange lookup $v6BlockTableId pref $v6BlockPref 2>/dev/null || true")
            }
        } else {
            commands.add("ip -6 rule add fwmark $markHex table $tableId pref $rulePref 2>/dev/null || true")
            commands.add("ip -6 route replace local ::/0 dev lo table $tableId 2>/dev/null || true")
        }

        // 4. Ingress TPROXY Mangle for Phone (IPv4 & IPv6)
        commands.add("iptables -w -t mangle -N $chainPreMangle 2>/dev/null || true")
        commands.add("iptables -w -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex 2>/dev/null || true")
        commands.add("iptables -w -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex 2>/dev/null || true")
        commands.add("iptables -w -t mangle -I PREROUTING 1 -j $chainPreMangle 2>/dev/null || true")

        commands.add("ip6tables -w -t mangle -N $chainPreMangleV6 2>/dev/null || true")
        commands.add("ip6tables -w -t mangle -A $chainPreMangleV6 -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex 2>/dev/null || true")
        commands.add("ip6tables -w -t mangle -A $chainPreMangleV6 -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex 2>/dev/null || true")
        commands.add("ip6tables -w -t mangle -I PREROUTING 1 -j $chainPreMangleV6 2>/dev/null || true")

        // 5. PHONE OUTBOUND MANGLE (Marks permitted UDP for TPROXY; NEVER marks STUN/WebRTC/DoT)
        commands.add("iptables -w -t mangle -N $chainOutMangle 2>/dev/null || true")
        commands.add("iptables -w -t mangle -A $chainOutMangle -m mark --mark $singboxBypassMark -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -w -t mangle -A $chainOutMangle -d $cleanedHost -j RETURN")
        }
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 53 -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 9 -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 853 -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 3478:3497 -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 5349:5350 -j RETURN")
        commands.add("iptables -w -t mangle -A $chainOutMangle -p udp --dport 19300:19320 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -w -t mangle -A $chainOutMangle -d $range -j RETURN")
        }

        if (supportsUdp) {
            for (uRange in uidRanges) {
                commands.add("iptables -w -t mangle -A $chainOutMangle -p udp -m owner --uid-owner $uRange -j MARK --set-mark $markHex")
            }
        }
        commands.add("iptables -w -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // IPv6 Outbound Mangle (Fallback DNS TPROXY if ip6table_nat is unavailable + Dual-Stack UDP)
        commands.add("ip6tables -w -t mangle -N $chainOutMangleV6 2>/dev/null || true")
        commands.add("ip6tables -w -t mangle -A $chainOutMangleV6 -m mark --mark $singboxBypassMark -j RETURN 2>/dev/null || true")
        if (hostIsIpv6) {
            commands.add("ip6tables -w -t mangle -A $chainOutMangleV6 -d $cleanedHost -j RETURN 2>/dev/null || true")
        }
        for (range in reservedV6) {
            commands.add("ip6tables -w -t mangle -A $chainOutMangleV6 -d $range -j RETURN 2>/dev/null || true")
        }
        if (settings.ipMode != IpMode.IPV4_ONLY && supportsUdp) {
            for (uRange in uidRanges) {
                commands.add("ip6tables -w -t mangle -A $chainOutMangleV6 -p udp ! --dport 3478:3497 -m owner --uid-owner $uRange -j MARK --set-mark $markHex 2>/dev/null || true")
            }
        }
        commands.add("ip6tables -w -t mangle -I OUTPUT 1 -j $chainOutMangleV6 2>/dev/null || true")

        // 6. PHONE NAT OUTPUT (Complete Cellular & Wi-Fi DNS Hijack + WebRTC Socket Source-IP Shield + TCP Redirect)
        // CRITICAL: We do NOT exempt --uid-owner 0 on port 53 because Android's system DNS resolver (netd)
        // runs as UID 0! Instead, sing-box sockets carry SINGBOX_BYPASS_MARK_HEX (0x20000000), allowing
        // sing-box to bypass NAT while 100% of netd (UID 0) and dns_tether (UID 1052) DNS queries on
        // Cellular (rmnet/ccmni) and Wi-Fi (wlan0) are hijacked into sing-box ($dnsPort)!
        commands.add("iptables -w -t nat -N $chainNatV4 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $chainNatV4 -m mark --mark $singboxBypassMark -j RETURN")
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")
        if (hostIsIpv4) {
            commands.add("iptables -w -t nat -A $chainNatV4 -d $cleanedHost -j RETURN")
        }

        // Hijack ALL IPv4 DNS (UDP & TCP Port 53) from netd (UID 0), dnsmasq, and all apps into sing-box
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")

        // Redirect STUN/TURN/WebRTC UDP sockets to loopback discard port 9 during connect()
        // so Chromium getsockname() binds to 127.0.0.1 instead of Cellular (rmnet) or Wi-Fi (wlan) IP!
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp --dport 3478:3497 -j REDIRECT --to-ports 9")
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp --dport 5349:5350 -j REDIRECT --to-ports 9")
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp --dport 19300:19320 -j REDIRECT --to-ports 9")
        commands.add("iptables -w -t nat -A $chainNatV4 -p udp --dport 853 -j REDIRECT --to-ports 9")

        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp --dport 853 -j RETURN")
        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp --dport 3478:3497 -j RETURN")
        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp --dport 5349:5350 -j RETURN")
        commands.add("iptables -w -t nat -A $chainNatV4 -p tcp --dport 19300:19320 -j RETURN")

        // System daemons (0-9999) bypass app TCP redirect (their DNS 53 was already hijacked above!)
        commands.add("iptables -w -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        // Always redirect FakeIP range (198.18.0.0/15) before reserved range checks
        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $chainNatV4 -p tcp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort")
        }

        // When strictWebRtcShield is active or proxy is TCP-based (SOCKS5/SOCKS4/HTTP/TCP_ONLY),
        // redirect ALL non-loopback UDP sockets from target apps to 127.0.0.1:9 during connect()!
        // This guarantees getsockname() returns 127.0.0.1 even if WebRTC probes private LAN/CGNAT
        // IPs (192.168.x.x, 10.x.x.x, 100.64.x.x) or random UDP ports (1..65535) on Cellular or Wi-Fi!
        if (!supportsUdp) {
            for (uRange in uidRanges) {
                commands.add("iptables -w -t nat -A $chainNatV4 -p udp ! -d 127.0.0.0/8 -m owner --uid-owner $uRange -j REDIRECT --to-ports 9")
            }
        }

        for (range in reservedV4) {
            commands.add("iptables -w -t nat -A $chainNatV4 -d $range -j RETURN")
        }

        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort")
        }
        commands.add("iptables -w -t nat -I OUTPUT 1 -j $chainNatV4")

        // IPv6 NAT OUTPUT (Intercepts Carrier/Wi-Fi IPv6 DNS 53 & WebRTC UDP connect() on IPv6)
        commands.add("ip6tables -w -t nat -N $chainNatV6 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -m mark --mark $singboxBypassMark -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        if (hostIsIpv6) {
            commands.add("ip6tables -w -t nat -A $chainNatV6 -d $cleanedHost -j RETURN 2>/dev/null || true")
        }
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp --dport 3478:3497 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp --dport 5349:5350 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp --dport 19300:19320 -j REDIRECT --to-ports 9 2>/dev/null || true")
        for (uRange in uidRanges) {
            commands.add("ip6tables -w -t nat -A $chainNatV6 -p udp ! -d ::1/128 -m owner --uid-owner $uRange -j REDIRECT --to-ports 9 2>/dev/null || true")
            if (settings.ipMode != IpMode.IPV4_ONLY) {
                commands.add("ip6tables -w -t nat -A $chainNatV6 -p tcp -d fc00::/18 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                commands.add("ip6tables -w -t nat -A $chainNatV6 -p tcp ! -d fe80::/10 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            }
        }
        commands.add("ip6tables -w -t nat -I OUTPUT 1 -j $chainNatV6 2>/dev/null || true")

        // 7. PHONE FILTER SHIELD (Rule ordering fixed: STUN/WebRTC/DoT/Discard blocks run BEFORE -o lo RETURN!)
        commands.add("iptables -w -N $chainFilter 2>/dev/null || true")

        // Allow sing-box's own marked outbound traffic first
        commands.add("iptables -w -A $chainFilter -m mark --mark $singboxBypassMark -j RETURN")

        // Drop all WebRTC/UDP traffic that was redirected to loopback discard port 9
        commands.add("iptables -w -A $chainFilter -p udp --dport 9 -j DROP")
        commands.add("iptables -w -A $chainFilter -p tcp --dport 9 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 9 -j DROP")

        // Hard Block Private DNS (DoT Port 853) BEFORE -o lo check so netd immediately falls back to Port 53
        commands.add("iptables -w -A $chainFilter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 853 -j DROP")
        commands.add("iptables -w -A $chainFilter -p udp --dport 853 -j DROP")

        // Hard Block WebRTC / STUN / TURN Ports on both UDP and TCP BEFORE -o lo check
        commands.add("iptables -w -A $chainFilter -p udp --dport 3478:3497 -j DROP")
        commands.add("iptables -w -A $chainFilter -p udp --dport 5349:5350 -j DROP")
        commands.add("iptables -w -A $chainFilter -p udp --dport 19300:19320 -j DROP")
        commands.add("iptables -w -A $chainFilter -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 3478:3497 -j DROP")
        commands.add("iptables -w -A $chainFilter -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 5349:5350 -j DROP")
        commands.add("iptables -w -A $chainFilter -p tcp --dport 19300:19320 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 19300:19320 -j DROP")

        // Deep-Packet STUN Magic Cookie (0x2112a442) block on ANY UDP port (catches non-standard port STUN)
        commands.add("iptables -w -A $chainFilter -p udp -m u32 --u32 \"0>>22&0x3C@12=0x2112a442\" -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $chainFilter -p udp -m string --algo bm --hex-string \"|2112a442|\" -j DROP 2>/dev/null || true")

        // Allow loopback and redirected local sing-box ports
        commands.add("iptables -w -A $chainFilter -o lo -j RETURN")
        commands.add("iptables -w -A $chainFilter -d 127.0.0.0/8 -j RETURN")
        commands.add("iptables -w -A $chainFilter -p tcp --dport $inboundPort -j RETURN")
        commands.add("iptables -w -A $chainFilter -p tcp --dport $socksPort -j RETURN")
        commands.add("iptables -w -A $chainFilter -p tcp --dport $dnsPort -j RETURN")
        commands.add("iptables -w -A $chainFilter -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -w -A $chainFilter -p udp --dport $tproxyPort -j RETURN")

        // Allow bootstrap DNS for root only
        commands.add("iptables -w -A $chainFilter -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")
        commands.add("iptables -w -A $chainFilter -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN")

        // Drop any unredirected Port 53 packet trying to leave a physical interface (wlan0 / rmnet_data0)
        commands.add("iptables -w -A $chainFilter -p udp --dport 53 -j DROP")
        commands.add("iptables -w -A $chainFilter -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp --dport 53 -j DROP")

        if (hostIsIpv4) {
            commands.add("iptables -w -A $chainFilter -d $cleanedHost -j RETURN")
        }

        // Allow essential system DHCP & NTP
        commands.add("iptables -w -A $chainFilter -p udp --dport 67:68 -j RETURN")
        commands.add("iptables -w -A $chainFilter -p udp --dport 123 -j RETURN")

        // Allow non-DNS system daemons (0-9999)
        commands.add("iptables -w -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")

        // Strict Kill-Switch for target apps on physical interfaces (drops any unproxied TCP/UDP/ICMP)
        for (uRange in uidRanges) {
            commands.add("iptables -w -A $chainFilter -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainFilter -p tcp -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -w -A $chainFilter -p udp -m owner --uid-owner $uRange -j DROP")
            commands.add("iptables -w -A $chainFilter -m owner --uid-owner $uRange -j DROP")
        }
        commands.add("iptables -w -I OUTPUT 1 -j $chainFilter")

        // 8. IPV6 FILTER SHIELD (Cellular 464XLAT Safe + Total App & DNS IPv6 Leak Prevention)
        commands.add("ip6tables -w -N $chainV6Filter 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -m mark --mark $singboxBypassMark -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -w -A $chainV6Filter -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 19300:19320 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p tcp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p tcp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p tcp --dport 19300:19320 -j DROP 2>/dev/null || true")

        // Allow loopback (so ip6tables -t nat REDIRECT to [::1]:$dnsPort or $inboundPort succeeds!)
        commands.add("ip6tables -w -A $chainV6Filter -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -d ::1/128 -j RETURN 2>/dev/null || true")

        // Reject any unredirected IPv6 Port 53 on external interfaces immediately (0.1ms fallback to IPv4 DNS)
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 53 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || ip6tables -w -A $chainV6Filter -p udp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -w -A $chainV6Filter -p tcp --dport 53 -j DROP 2>/dev/null || true")

        // Allow essential ICMPv6 Neighbor Discovery / Router Discovery and DHCPv6 + clatd (UID 1029) & root (UID 0)
        // so IPv6-only cellular networks using 464XLAT (v4-rmnet_data+) stay online!
        commands.add("ip6tables -w -A $chainV6Filter -p ipv6-icmp -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -p udp --dport 546:547 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $chainV6Filter -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        // Block all unproxied IPv6 traffic from apps (10000-99999)
        for (uRange in uidRanges) {
            commands.add("ip6tables -w -A $chainV6Filter -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -w -A $chainV6Filter -p tcp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("ip6tables -w -A $chainV6Filter -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -w -A $chainV6Filter -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("ip6tables -w -A $chainV6Filter -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
        }
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -w -A $chainV6Filter -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -w -I OUTPUT 1 -j $chainV6Filter 2>/dev/null || true")

        // 9. HOTSPOT ROUTING & LEAK PREVENTION
        if (settings.routeHotspot && user == 0) {
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 18500 2>/dev/null || true")
            commands.add("ip route replace local 0.0.0.0/0 dev lo table $hsTableId 2>/dev/null || true")

            // Block IPv6 on tether interfaces to prevent IPv6 bypass/leak on tethered clients
            commands.add("ip6tables -w -N $chainHotspotV6Block 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -w -A $chainHotspotV6Block -i $iface -j DROP 2>/dev/null || true")
                commands.add("ip6tables -w -A $chainHotspotV6Block -o $iface -j DROP 2>/dev/null || true")
            }
            commands.add("ip6tables -w -I FORWARD 1 -j $chainHotspotV6Block 2>/dev/null || true")

            // Hotspot NAT: Intercept DNS 53 and TCP into sing-box
            commands.add("iptables -w -t nat -N $chainHotspotNat 2>/dev/null || true")
            if (hostIsIpv4) {
                commands.add("iptables -w -t nat -A $chainHotspotNat -d $cleanedHost -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t nat -A $chainHotspotNat -i $iface -p udp --dport 67:68 -j RETURN")
                commands.add("iptables -w -t nat -A $chainHotspotNat -i $iface -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -w -t nat -A $chainHotspotNat -i $iface -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -w -t nat -A $chainHotspotNat -i $iface -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $inboundPort")
            }
            for (range in reservedV4) {
                commands.add("iptables -w -t nat -A $chainHotspotNat -d $range -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t nat -A $chainHotspotNat -i $iface -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -w -t nat -I PREROUTING 1 -j $chainHotspotNat")

            // Hotspot Mangle for TPROXY
            commands.add("iptables -w -t mangle -N $chainHotspotMangle 2>/dev/null || true")
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t mangle -A $chainHotspotMangle -i $iface -p udp --dport 67:68 -j RETURN")
            }
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -p udp --dport 53 -j RETURN")
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -p udp --dport 853 -j RETURN")
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -p udp --dport 3478:3497 -j RETURN")
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -p udp --dport 5349:5350 -j RETURN")
            commands.add("iptables -w -t mangle -A $chainHotspotMangle -p udp --dport 19300:19320 -j RETURN")
            for (range in reservedV4) {
                commands.add("iptables -w -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (hostIsIpv4) {
                commands.add("iptables -w -t mangle -A $chainHotspotMangle -d $cleanedHost -j RETURN")
            }
            if (supportsUdp) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -w -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex 2>/dev/null || true")
                }
            }
            commands.add("iptables -w -t mangle -I PREROUTING 1 -j $chainHotspotMangle")

            // Hotspot FORWARD chain Leak-Shield
            commands.add("iptables -w -N $chainHotspotFwd 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --dport 67:68 -j ACCEPT")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --sport 67:68 -j ACCEPT")
                // Block DoT & STUN/WebRTC leaks on connected hotspot clients
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $chainHotspotFwd -i $iface -p tcp --dport 853 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --dport 853 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --dport 3478:3497 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --dport 5349:5350 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp --dport 19300:19320 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p tcp --dport 3478:3497 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p tcp --dport 5349:5350 -j DROP")
                commands.add("iptables -w -A $chainHotspotFwd -i $iface -p tcp --dport 19300:19320 -j DROP")
                if (!supportsUdp) {
                    commands.add("iptables -w -A $chainHotspotFwd -i $iface -p udp ! --dport 53 -j DROP")
                }
            }
            commands.add("iptables -w -I FORWARD 1 -j $chainHotspotFwd")
        }

        // 10. Flush Android DNS caches across all interfaces (Wi-Fi & Cellular)
        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return commands
    }

    /**
     * Re-asserts kernel sysctls, Private DNS off state, top-of-chain hook positions, and flushes
     * stale resolver caches whenever the device transitions between Cellular (rmnet/ccmni) and Wi-Fi (wlan).
     */
    fun generateHandoverRefreshCommands(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): List<String> {
        val key = "u" + user + "_s" + slot
        val chainNatV4 = "NAMELESS_U" + user + "_S" + slot
        val chainNatV6 = "NAMELESS_U" + user + "_S" + slot + "_V6"
        val chainPreMangle = "NAMELESS_PRE_" + key
        val chainOutMangle = "NAMELESS_OUT_" + key
        val chainFilter = "NAMELESS_FILTER_" + key
        val chainV6Filter = "NAMELESS_V6_FILTER_" + key

        return listOf(
            "echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done",
            "echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done",
            "settings put global private_dns_mode off 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $chainFilter 2>/dev/null; iptables -w -I OUTPUT 1 -j $chainFilter 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $chainNatV4 2>/dev/null; iptables -w -t nat -I OUTPUT 1 -j $chainNatV4 2>/dev/null || true",
            "iptables -w -t mangle -D OUTPUT -j $chainOutMangle 2>/dev/null; iptables -w -t mangle -I OUTPUT 1 -j $chainOutMangle 2>/dev/null || true",
            "iptables -w -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null; iptables -w -t mangle -I PREROUTING 1 -j $chainPreMangle 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $chainV6Filter 2>/dev/null; ip6tables -w -I OUTPUT 1 -j $chainV6Filter 2>/dev/null || true",
            "ip6tables -w -t nat -D OUTPUT -j $chainNatV6 2>/dev/null; ip6tables -w -t nat -I OUTPUT 1 -j $chainNatV6 2>/dev/null || true",
            "cmd connectivity flush-dns 2>/dev/null || true",
            "ndc resolver flushdefaultif 2>/dev/null || true"
        )
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
        val v6BlockTableId = ProfileManager.getV6BlockTableId(user, slot)
        val v6BlockPref = 8900 + (user * 10) + slot
        val markHex = ProfileManager.getMarkHex(user, slot)
        val hsTableId = ProfileManager.getHotspotTableId(slot)
        val hsMarkHex = ProfileManager.getHotspotMarkHex(slot)

        return listOf(
            "echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true",
            "for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 0 > \$f 2>/dev/null || true; done",
            "iptables -w -D FORWARD -j $chainHotspotFwd 2>/dev/null || true",
            "iptables -w -F $chainHotspotFwd 2>/dev/null || true",
            "iptables -w -X $chainHotspotFwd 2>/dev/null || true",
            "ip6tables -w -D FORWARD -j $chainHotspotV6Block 2>/dev/null || true",
            "ip6tables -w -F $chainHotspotV6Block 2>/dev/null || true",
            "ip6tables -w -X $chainHotspotV6Block 2>/dev/null || true",
            "iptables -w -t nat -D PREROUTING -j $chainHotspotNat 2>/dev/null || true",
            "iptables -w -t nat -F $chainHotspotNat 2>/dev/null || true",
            "iptables -w -t nat -X $chainHotspotNat 2>/dev/null || true",
            "iptables -w -t mangle -D PREROUTING -j $chainHotspotMangle 2>/dev/null || true",
            "iptables -w -t mangle -F $chainHotspotMangle 2>/dev/null || true",
            "iptables -w -t mangle -X $chainHotspotMangle 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $chainFilter 2>/dev/null || true",
            "iptables -w -F $chainFilter 2>/dev/null || true",
            "iptables -w -X $chainFilter 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $chainV6Filter 2>/dev/null || true",
            "ip6tables -w -F $chainV6Filter 2>/dev/null || true",
            "ip6tables -w -X $chainV6Filter 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $chainNatV4 2>/dev/null || true",
            "iptables -w -t nat -F $chainNatV4 2>/dev/null || true",
            "iptables -w -X -t nat $chainNatV4 2>/dev/null || true",
            "ip6tables -w -t nat -D OUTPUT -j $chainNatV6 2>/dev/null || true",
            "ip6tables -w -t nat -F $chainNatV6 2>/dev/null || true",
            "ip6tables -w -t nat -X $chainNatV6 2>/dev/null || true",
            "iptables -w -t mangle -D PREROUTING -j $chainPreMangle 2>/dev/null || true",
            "iptables -w -t mangle -F $chainPreMangle 2>/dev/null || true",
            "iptables -w -t mangle -X $chainPreMangle 2>/dev/null || true",
            "iptables -w -t mangle -D OUTPUT -j $chainOutMangle 2>/dev/null || true",
            "iptables -w -t mangle -F $chainOutMangle 2>/dev/null || true",
            "iptables -w -t mangle -X $chainOutMangle 2>/dev/null || true",
            "ip6tables -w -t mangle -D PREROUTING -j $chainPreMangleV6 2>/dev/null || true",
            "ip6tables -w -t mangle -F $chainPreMangleV6 2>/dev/null || true",
            "ip6tables -w -t mangle -X $chainPreMangleV6 2>/dev/null || true",
            "ip6tables -w -t mangle -D OUTPUT -j $chainOutMangleV6 2>/dev/null || true",
            "ip6tables -w -t mangle -F $chainOutMangleV6 2>/dev/null || true",
            "ip6tables -w -t mangle -X $chainOutMangleV6 2>/dev/null || true",
            "ip rule del fwmark $markHex table $tableId 2>/dev/null || true",
            "ip route flush table $tableId 2>/dev/null || true",
            "ip -6 rule del fwmark $markHex table $tableId 2>/dev/null || true",
            "ip -6 route flush table $tableId 2>/dev/null || true",
            "while ip -6 rule del pref $v6BlockPref 2>/dev/null; do :; done",
            "ip -6 route flush table $v6BlockTableId 2>/dev/null || true",
            "ip rule del fwmark $hsMarkHex table $hsTableId 2>/dev/null || true",
            "ip route flush table $hsTableId 2>/dev/null || true"
        )
    }
}
