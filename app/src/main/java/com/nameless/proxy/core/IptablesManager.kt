package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})(/\d{1,2})?$""")
    private val IPV6_REGEX = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")

    // Canonical single-instance chain names so stopProxy() cleans up in <120ms
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
        resolvedServerHost: String? = null
    ): List<String> {
        val user = ProfileManager.androidUserId
        val socksPort = inboundPort + 1
        val dnsPort = inboundPort + 2
        val stunPort = inboundPort + 3

        val sdkSandboxStart = user * 100000 + 20000
        val sdkSandboxEnd = user * 100000 + 29999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        // Includes wlan+ as well because when upstream internet is Cellular (rmnet_data0),
        // Android runs the Wi-Fi Hotspot AP directly on wlan0!
        val tetherInterfaces = listOf("ap+", "swlan+", "softap+", "rndis+", "usb+", "p2p+", "ncm+", "wlan+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.0.0.0/24", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val effectiveHost = resolvedServerHost?.takeIf { it.isNotBlank() } ?: rawHost
        val hostIsIpv4 = isValidIpv4(effectiveHost)
        val hostIsIpv6 = isValidIpv6(effectiveHost)

        val uidRanges = if (selectedUids.isNullOrEmpty()) {
            listOf("10000-9999999")
        } else {
            selectedUids.distinct().map { it.toString() } +
                listOf("90000-99999", "$sdkSandboxStart-$sdkSandboxEnd", "$isolatedStart-$isolatedEnd")
        }

        val commands = mutableListOf<String>()

        // 1. Fast clean of canonical chains
        commands.addAll(generateDisableCommands())

        // 2. Kernel sysctl tuning + flush stale conntrack entries from before tunnel start
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/default/route_localnet 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")
        commands.add("conntrack -F 2>/dev/null || true")

        // 3. HOTSPOT / LAN RETURN ROUTING (Pref 8500..8505)
        // Ensures StunResponder's UDP responses to tethered Hotspot clients (192.168.x.x / 172.16.x.x / 10.x.x.x)
        // look up Android's local_network / main tables BEFORE netd's pref 22000 default cellular rule!
        commands.add("ip rule add to 192.168.0.0/16 lookup local_network pref 8500 2>/dev/null || true")
        commands.add("ip rule add to 192.168.0.0/16 lookup main pref 8501 2>/dev/null || true")
        commands.add("ip rule add to 172.16.0.0/12 lookup local_network pref 8502 2>/dev/null || true")
        commands.add("ip rule add to 172.16.0.0/12 lookup main pref 8503 2>/dev/null || true")
        commands.add("ip rule add to 10.0.0.0/8 lookup local_network pref 8504 2>/dev/null || true")
        commands.add("ip rule add to 10.0.0.0/8 lookup main pref 8505 2>/dev/null || true")

        // IPv6 App-Level Leak Block (Uses 'unreachable default' so Chromium Happy Eyeballs immediately
        // falls back to IPv4 in <1ms instead of failing with ERR_CONNECTION_RESET!)
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip -6 route replace unreachable default table $APP_BLOCK_TABLE_V6 2>/dev/null || ip -6 route replace unreachable ::/0 table $APP_BLOCK_TABLE_V6 2>/dev/null || true")
            for (uRange in uidRanges) {
                commands.add("ip -6 rule add uidrange $uRange lookup $APP_BLOCK_TABLE_V6 pref $APP_BLOCK_PREF_V6 2>/dev/null || true")
            }
        }

        // 4. PHONE NAT OUTPUT (DNS Hijack + STUN Responder Redirect + QUIC Fast-Reject + TCP Redirect)
        commands.add("iptables -w -t nat -N $CHAIN_NAT_V4 2>/dev/null || true")
        // Exempt StunResponder's own outgoing UDP reply packets from source port $stunPort
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        // Exempt root bootstrap DNS (9.9.9.9:53)
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        if (hostIsIpv4) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }

        // Hijack 100% of IPv4 DNS (UDP & TCP Port 53) from netd (UID 0), dnsmasq, Chrome, and all apps into sing-box
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")

        // Redirect QUIC (UDP 80/443) and DoT (UDP 853) to port 9 for instant ICMP Port Unreachable in CHAIN_FILTER_V4
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 80 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 443 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 853 -j REDIRECT --to-ports 9 2>/dev/null || true")

        // Redirect STUN/WebRTC UDP ports to our local RFC 5389 StunResponder ($stunPort) so WebRTC shows the US Proxy IP!
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 3478:3497 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 5349:5350 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 19300:19320 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")

        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 853 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 3478:3497 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 5349:5350 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 19300:19320 -j RETURN 2>/dev/null || true")

        // System daemons (0-9999, including sing-box UID 0) bypass app TCP redirect
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        // Redirect FakeIP range (198.18.0.0/15) before reserved range checks
        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        }

        for (range in reservedV4) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $range -j RETURN 2>/dev/null || true")
        }

        // Redirect all remaining public UDP from target apps to StunResponder ($stunPort) and all TCP to sing-box ($inboundPort)
        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -m owner --uid-owner $uRange -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        }
        commands.add("iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true")

        // 5. IPv6 NAT OUTPUT (Crucial for IPv6 Cellular networks like Jazz LTE 2404:3100:...)
        commands.add("ip6tables -w -t nat -N $CHAIN_NAT_V6 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV6} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        if (hostIsIpv6) {
            commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }
        commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
        for (uRange in uidRanges) {
            commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p udp ! -d ::1/128 -m owner --uid-owner $uRange -j REDIRECT --to-ports 9 2>/dev/null || true")
            // Always redirect any app IPv6 TCP connection (even cached AAAA records) into sing-box [::]:$inboundPort
            commands.add("ip6tables -w -t nat -A $CHAIN_NAT_V6 -p tcp ! -d ::1/128 ! -d fe80::/10 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        }
        commands.add("ip6tables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V6 2>/dev/null || true")

        // 6. PHONE FILTER SHIELD (Fast REJECT --reject-with icmp-port-unreachable on port 9 so QUIC fails in 0.05ms!)
        commands.add("iptables -w -N $CHAIN_FILTER_V4 2>/dev/null || true")
        // Allow StunResponder's outgoing UDP replies immediately
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j DROP 2>/dev/null || true")

        // Hard Block Private DNS (DoT Port 853) and TCP STUN (so WebRTC always uses UDP STUN -> StunResponder!)
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j DROP 2>/dev/null || true")

        // Allow loopback and redirected local sing-box + StunResponder ports
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -o lo -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $socksPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport $stunPort -j RETURN 2>/dev/null || true")

        // Allow bootstrap DNS for root only
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")

        // Block any unredirected Port 53 packet trying to leave a physical interface
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 53 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 53 -j DROP 2>/dev/null || true")

        if (hostIsIpv4) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }

        // Allow essential system DHCP, NTP & LAN/Hotspot subnets
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 67:68 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 123 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 192.168.0.0/16 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 172.16.0.0/12 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 10.0.0.0/8 -j RETURN 2>/dev/null || true")

        // Allow non-DNS system daemons (0-9999)
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        // Strict Kill-Switch for target apps on physical interfaces
        for (uRange in uidRanges) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
        }
        commands.add("iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true")

        // 7. IPV6 FILTER SHIELD
        commands.add("ip6tables -w -N $CHAIN_FILTER_V6 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 9 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport 853 -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 853 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 19300:19320 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -d ::1/128 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 53 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport 53 -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p tcp --dport 53 -j DROP 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p ipv6-icmp -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp --dport 546:547 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        // Reject unredirected app IPv6 with icmp6-adm-prohibited (NOT tcp-reset) so Happy Eyeballs falls back to IPv4!
        for (uRange in uidRanges) {
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p tcp -m owner --uid-owner $uRange -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p tcp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp6-adm-prohibited 2>/dev/null || ip6tables -w -A $CHAIN_FILTER_V6 -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
        }
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true")

        // 8. HOTSPOT ROUTING & WEBRTC US-IP SYNTHESIS (For Windows / Mac / iOS Hotspot Clients!)
        if (settings.routeHotspot) {
            commands.add("ip6tables -w -N $CHAIN_HS_V6 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -w -A $CHAIN_HS_V6 -i $iface -j DROP 2>/dev/null || true")
                commands.add("ip6tables -w -A $CHAIN_HS_V6 -o $iface -j DROP 2>/dev/null || true")
            }
            commands.add("ip6tables -w -I FORWARD 1 -j $CHAIN_HS_V6 2>/dev/null || true")

            commands.add("iptables -w -t nat -N $CHAIN_HS_NAT 2>/dev/null || true")
            if (hostIsIpv4) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -d $effectiveHost -j RETURN 2>/dev/null || true")
            }
            for (iface in tetherInterfaces) {
                // Allow DHCP
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 67:68 -j RETURN 2>/dev/null || true")
                // Redirect Hotspot DNS (UDP & TCP 53) to sing-box
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
                // Let QUIC (UDP 80/443) and DoT (UDP 853) fall through to FORWARD where they get immediate ICMP Port Unreachable
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 80 -j RETURN 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 443 -j RETURN 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 853 -j RETURN 2>/dev/null || true")
                // Redirect Hotspot STUN/WebRTC UDP ports to our local StunResponder ($stunPort) so Windows Hotspot shows US WebRTC IP!
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 3478:3497 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 5349:5350 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp --dport 19300:19320 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
                // Redirect FakeIP TCP to sing-box and FakeIP UDP (e.g. stun.l.google.com resolved to 198.18.0.x) to StunResponder!
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            }
            for (range in reservedV4) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -d $range -j RETURN 2>/dev/null || true")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p tcp -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            }
            commands.add("iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true")

            // Explicitly allow redirected Hotspot packets in INPUT so Android's tetherctrl never blocks StunResponder or DNS
            commands.add("iptables -w -N $CHAIN_HS_IN 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $inboundPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $stunPort -j ACCEPT 2>/dev/null || true")
            commands.add("iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true")

            // Block ALL unredirected/pre-existing Hotspot packets in FORWARD!
            // Since PREROUTING REDIRECT delivers all valid TCP, DNS, and STUN packets to INPUT (never FORWARD),
            // any packet from $iface that reaches FORWARD is a pre-existing direct connection from before tunnel start!
            commands.add("iptables -w -N $CHAIN_HS_FWD 2>/dev/null || true")
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p udp --dport 67:68 -j ACCEPT 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p udp --sport 67:68 -j ACCEPT 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p tcp -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_HS_FWD -i $iface -p tcp -j DROP 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -p udp -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_HS_FWD -i $iface -p udp -j DROP 2>/dev/null || true")
                commands.add("iptables -w -A $CHAIN_HS_FWD -i $iface -j DROP 2>/dev/null || true")
            }
            commands.add("iptables -w -I FORWARD 1 -j $CHAIN_HS_FWD 2>/dev/null || true")
        }

        // 9. Flush Android DNS caches across all interfaces
        commands.add("cmd connectivity flush-dns 2>/dev/null || true")
        commands.add("ndc resolver flushdefaultif 2>/dev/null || true")

        return commands
    }

    fun generateHandoverRefreshCommands(): List<String> {
        return listOf(
            "echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \$f 2>/dev/null || true; done",
            "echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \$f 2>/dev/null || true; done",
            "settings put global private_dns_mode off 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $CHAIN_FILTER_V4 2>/dev/null; iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $CHAIN_NAT_V4 2>/dev/null; iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $CHAIN_FILTER_V6 2>/dev/null; ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true",
            "ip6tables -w -t nat -D OUTPUT -j $CHAIN_NAT_V6 2>/dev/null; ip6tables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V6 2>/dev/null || true",
            "iptables -w -t nat -D PREROUTING -j $CHAIN_HS_NAT 2>/dev/null; iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true",
            "iptables -w -D INPUT -j $CHAIN_HS_IN 2>/dev/null; iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true",
            "iptables -w -D FORWARD -j $CHAIN_HS_FWD 2>/dev/null; iptables -w -I FORWARD 1 -j $CHAIN_HS_FWD 2>/dev/null || true",
            "cmd connectivity flush-dns 2>/dev/null || true",
            "ndc resolver flushdefaultif 2>/dev/null || true"
        )
    }

    /**
     * Ultra-fast single-pass cleanup (~34 commands total), completing in <120ms!
     */
    fun generateDisableCommands(
        user: Int = ProfileManager.androidUserId,
        slot: Int = ProfileManager.activeSlot
    ): List<String> {
        val legacyNatV4 = "NAMELESS_U${user}_S$slot"
        val legacyFilter = "NAMELESS_FILTER_u${user}_s$slot"
        return listOf(
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
