package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})(/\d{1,2})?$""")
    private val IPV6_REGEX = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")

    const val CHAIN_NAT_V4 = "NAMELESS_NAT"
    const val CHAIN_NAT_V6 = "NAMELESS_V6_NAT"
    const val CHAIN_FILTER_V4 = "NAMELESS_FILTER"
    const val CHAIN_FILTER_V6 = "NAMELESS_V6_FILTER"
    const val CHAIN_HS_NAT = "NAMELESS_HS_NAT"
    const val CHAIN_HS_IN = "NAMELESS_HS_IN"
    const val CHAIN_HS_FWD = "NAMELESS_HS_FWD"
    const val CHAIN_HS_V6 = "NAMELESS_HS_V6"

    private const val APP_ROUTE_TABLE_V4 = 2088
    private const val APP_ROUTE_PREF_V4 = 8800
    private const val APP_BLOCK_TABLE_V6 = 2288
    private const val APP_BLOCK_PREF_V6 = 8900

    fun isValidIpv4(host: String): Boolean = IPV4_REGEX.matches(host.trim())

    fun isValidIpv6(host: String): Boolean {
        val cleaned = ConfigGenerator.cleanHost(host)
        return cleaned.contains(":") && IPV6_REGEX.matches(cleaned)
    }

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null,
        resolvedServerHost: String? = null,
        skipInitialClean: Boolean = false
    ): List<String> {
        val user = ProfileManager.androidUserId
        val socksPort = inboundPort + 1
        val dnsPort = inboundPort + 2
        val stunPort = inboundPort + 3
        val hsDnsPort = inboundPort + 4
        val hsStunPort = inboundPort + 5
        val mark = ConfigGenerator.SINGBOX_ROUTING_MARK

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val sdkSandboxStart = user * 100000 + 20000
        val sdkSandboxEnd = user * 100000 + 29999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tetherInterfaces = listOf("ap+", "swlan+", "softap+", "rndis+", "usb+", "p2p+", "ncm+", "wlan+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val effectiveHost = resolvedServerHost?.takeIf { it.isNotBlank() } ?: rawHost
        val hostIsIpv4 = isValidIpv4(effectiveHost)

        val wholeProfile = selectedUids.isNullOrEmpty()
        val uidRanges = if (wholeProfile) {
            listOf("$appStart-$appEnd", "$sdkSandboxStart-$sdkSandboxEnd", "$isolatedStart-$isolatedEnd")
        } else {
            selectedUids!!.distinct().map { it.toString() } +
                listOf("$sdkSandboxStart-$sdkSandboxEnd", "$isolatedStart-$isolatedEnd")
        }

        val commands = mutableListOf<String>()

        if (!skipInitialClean) {
            commands.addAll(generateDisableCommands(user, ProfileManager.activeSlot))
        }

        // 1. Kernel sysctl configuration
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/default/route_localnet 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")

        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true")
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true")
            commands.add("for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 1 > \$f 2>/dev/null || true; done")
            commands.add("echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true")
        }

        // 2. Hotspot subnet return routing
        if (settings.routeHotspot) {
            commands.add("ip rule add to 192.168.0.0/16 lookup local_network pref 8500 2>/dev/null || true")
            commands.add("ip rule add to 192.168.0.0/16 lookup main pref 8501 2>/dev/null || true")
            commands.add("ip rule add to 172.16.0.0/12 lookup local_network pref 8502 2>/dev/null || true")
            commands.add("ip rule add to 172.16.0.0/12 lookup main pref 8503 2>/dev/null || true")
        }

        // 3. PHONE NAT OUTPUT
        commands.add("iptables -w -t nat -N $CHAIN_NAT_V4 2>/dev/null || true")
        // Rule #1: Exempt sing-box's own outbound connections tagged with routing_mark 6666
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -m mark --mark $mark -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $hsStunPort -j RETURN 2>/dev/null || true")
        if (hostIsIpv4) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }

        // Tunnel Android Private DNS (DNS-over-TLS, TCP Port 853 from netd UID 0 & apps) directly into sing-box!
        // This allows Private DNS (Cloudflare / dns.google) to stay ON in Android Settings and exit via the US Proxy!
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")

        // Redirect Phone DNS (UDP & TCP Port 53) into sing-box's 127.0.0.1:$dnsPort listener
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")

        // Fast-reject QUIC (UDP 80/443) and DoQ (UDP 853) via port 9 so apps use TCP 443/853 through sing-box
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 80 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 443 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 853 -j REDIRECT --to-ports 9 2>/dev/null || true")

        // Redirect Phone STUN/WebRTC UDP ports into StunResponder ONLY when strictWebRtcShield is enabled
        if (settings.strictWebRtcShield) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 3478:3497 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 5349:5350 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 19300:19320 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 3478:3497 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 5349:5350 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 19300:19320 -j RETURN 2>/dev/null || true")
        }

        // Non-DNS system daemons (0-9999) bypass app TCP redirect
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        if (wholeProfile) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            if (settings.strictWebRtcShield) {
                commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            }
            for (range in reservedV4) {
                commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $range -j RETURN 2>/dev/null || true")
            }
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        } else {
            for (uRange in uidRanges) {
                commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                if (settings.strictWebRtcShield) {
                    commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
                }
            }
            for (range in reservedV4) {
                commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $range -j RETURN 2>/dev/null || true")
            }
            for (uRange in uidRanges) {
                commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            }
        }
        commands.add("iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true")

        // 4. PHONE FILTER SHIELD
        commands.add("iptables -w -N $CHAIN_FILTER_V4 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m mark --mark $mark -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $hsStunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j DROP 2>/dev/null || true")

        if (settings.strictWebRtcShield) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j DROP 2>/dev/null || true")
        }

        // Allow loopback, established replies, and all local proxy/DNS/STUN ports
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -o lo -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -s 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m state --state ESTABLISHED,RELATED -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --sport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $socksPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --sport $socksPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $hsDnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $hsDnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $hsDnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $hsStunPort -j RETURN 2>/dev/null || true")

        if (hostIsIpv4) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }

        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 67:68 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 123 -j RETURN 2>/dev/null || true")
        for (range in reservedV4) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -d $range -j RETURN 2>/dev/null || true")
        }

        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        if (wholeProfile) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -j DROP 2>/dev/null || true")
        } else {
            for (uRange in uidRanges) {
                commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            }
        }
        commands.add("iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true")

        // 5. IPV6 FILTER SHIELD
        commands.add("ip6tables -w -N $CHAIN_FILTER_V6 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -d ::1/128 -j RETURN 2>/dev/null || true")
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true")

        // 6. HOTSPOT ROUTING & STUN RESPONDER (Only when settings.routeHotspot == true)
        if (settings.routeHotspot) {
            commands.add("ip6tables -w -N $CHAIN_HS_V6 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -w -A $CHAIN_HS_V6 -i $iface -j DROP 2>/dev/null || true")
                commands.add("ip6tables -w -A $CHAIN_HS_V6 -o $iface -j DROP 2>/dev/null || true")
            }
            commands.add("ip6tables -w -I FORWARD 1 -j $CHAIN_HS_V6 2>/dev/null || true")

            commands.add("iptables -w -t nat -N $CHAIN_HS_NAT 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i lo -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i rmnet+ -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i v4-rmnet+ -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i ccmni+ -j RETURN 2>/dev/null || true")
            if (hostIsIpv4) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -d $effectiveHost -j RETURN 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -s $effectiveHost -j RETURN 2>/dev/null || true")
            }
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 67:68 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 53 -j REDIRECT --to-ports $hsDnsPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp --dport 53 -j REDIRECT --to-ports $hsDnsPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 80 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 443 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 853 -j RETURN 2>/dev/null || true")
            if (settings.strictWebRtcShield) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 3478:3497 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 5349:5350 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 19300:19320 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            }
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            for (range in reservedV4) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -d $range -j RETURN 2>/dev/null || true")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p tcp -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                if (settings.strictWebRtcShield) {
                    commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
                }
            }
            commands.add("iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true")

            commands.add("iptables -w -N $CHAIN_HS_IN 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -i lo -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $inboundPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $socksPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $stunPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $hsDnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $hsDnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $hsStunPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true")

            commands.add("iptables -w -N $CHAIN_HS_FWD 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_FWD -i rmnet+ -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_FWD -i v4-rmnet+ -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_FWD -i ccmni+ -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_FWD -p udp --dport 67:68 -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_FWD -p udp --sport 67:68 -j ACCEPT 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p tcp -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_HS_FWD -i $iface -p tcp -j DROP 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p udp -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_HS_FWD -i $iface -p udp -j DROP 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -j DROP 2>/dev/null || true")
            }
            commands.add("iptables -w -I FORWARD 1 -j $CHAIN_HS_FWD 2>/dev/null || true")
        }

        // 7. Flush Android DNS caches
        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return commands
    }

    fun generateHandoverRefreshCommands(routeHotspot: Boolean = true): List<String> {
        val list = mutableListOf(
            "echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done",
            "echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done",
            "for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 1 > \$f 2>/dev/null || true; done",
            "echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $CHAIN_FILTER_V4 2>/dev/null; iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $CHAIN_NAT_V4 2>/dev/null; iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $CHAIN_FILTER_V6 2>/dev/null; ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true"
        )
        if (routeHotspot) {
            list.add("iptables -w -t nat -D PREROUTING -j $CHAIN_HS_NAT 2>/dev/null; iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true")
            list.add("iptables -w -D INPUT -j $CHAIN_HS_IN 2>/dev/null; iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true")
            list.add("iptables -w -D FORWARD -j $CHAIN_HS_FWD 2>/dev/null; iptables -w -I FORWARD 1 -j $CHAIN_HS_FWD 2>/dev/null || true")
        }
        list.add("cmd connectivity flush-dns 2>/dev/null || true")
        list.add("ndc resolver flushdefaultif 2>/dev/null || true")
        return list
    }

    fun generateDisableCommands(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): List<String> {
        val legacyNatV4 = "NAMELESS_U${user}_S$slot"
        val legacyFilter = "NAMELESS_FILTER_u${user}_s$slot"
        return listOf(
            "echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true",
            "for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 0 > \$f 2>/dev/null || true; done",
            "for p in 8500 8501 8502 8503 8504 8505 $APP_ROUTE_PREF_V4; do while ip rule del pref \$p 2>/dev/null; do :; done; done",
            "ip route flush table $APP_ROUTE_TABLE_V4 2>/dev/null || true",
            "while ip -6 rule del pref $APP_BLOCK_PREF_V6 2>/dev/null; do :; done",
            "ip -6 route flush table $APP_BLOCK_TABLE_V6 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -F $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -X $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $CHAIN_NAT_V4 2>/dev/null || true",
            "iptables -w -t nat -F $CHAIN_NAT_V4 2>/dev/null || true",
            "iptables -w -t nat -X $CHAIN_NAT_V4 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $CHAIN_FILTER_V6 2>/dev/null || true",
            "ip6tables -w -F $CHAIN_FILTER_V6 2>/dev/null || true",
            "ip6tables -w -X $CHAIN_FILTER_V6 2>/dev/null || true",
            "ip6tables -w -t nat -D OUTPUT -j $CHAIN_NAT_V6 2>/dev/null || true",
            "ip6tables -w -t nat -F $CHAIN_NAT_V6 2>/dev/null || true",
            "ip6tables -w -t nat -X $CHAIN_NAT_V6 2>/dev/null || true",
            "iptables -w -D INPUT -j $CHAIN_HS_IN 2>/dev/null || true",
            "iptables -w -F $CHAIN_HS_IN 2>/dev/null || true",
            "iptables -w -X $CHAIN_HS_IN 2>/dev/null || true",
            "iptables -w -D FORWARD -j $CHAIN_HS_FWD 2>/dev/null || true",
            "iptables -w -F $CHAIN_HS_FWD 2>/dev/null || true",
            "iptables -w -X $CHAIN_HS_FWD 2>/dev/null || true",
            "iptables -w -t nat -D PREROUTING -j $CHAIN_HS_NAT 2>/dev/null || true",
            "iptables -w -t nat -F $CHAIN_HS_NAT 2>/dev/null || true",
            "iptables -w -t nat -X $CHAIN_HS_NAT 2>/dev/null || true",
            "ip6tables -w -D FORWARD -j $CHAIN_HS_V6 2>/dev/null || true",
            "ip6tables -w -F $CHAIN_HS_V6 2>/dev/null || true",
            "ip6tables -w -X $CHAIN_HS_V6 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $legacyFilter 2>/dev/null; iptables -w -F $legacyFilter 2>/dev/null; iptables -w -X $legacyFilter 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $legacyNatV4 2>/dev/null; iptables -w -t nat -F $legacyNatV4 2>/dev/null; iptables -w -X $legacyNatV4 2>/dev/null || true"
        )
    }
}
