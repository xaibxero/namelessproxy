package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})(/\d{1,2})?$""")
    private val IPV6_REGEX = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")

    const val CHAIN_NAT_V4 = "NAMELESS_NAT"
    const val CHAIN_POST_V4 = "NAMELESS_POST"
    const val CHAIN_NAT_V6 = "NAMELESS_V6_NAT"
    const val CHAIN_FILTER_V4 = "NAMELESS_FILTER"
    const val CHAIN_FILTER_V6 = "NAMELESS_V6_FILTER"
    const val CHAIN_HS_NAT = "NAMELESS_HS_NAT"
    const val CHAIN_HS_IN = "NAMELESS_HS_IN"
    const val CHAIN_HS_FWD = "NAMELESS_HS_FWD"
    const val CHAIN_HS_V6 = "NAMELESS_HS_V6"

    private const val APP_ROUTE_TABLE_V4 = 2088
    private const val APP_ROUTE_PREF_V4 = 8800
    private const val LO_VALID_PREF_V4 = 8805
    private const val FAKEIP_VALID_PREF_V4 = 8806
    private const val CELL_VALID_PREF_BASE = 31000
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
        val hsInboundPort = inboundPort + 6

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val sdkSandboxStart = user * 100000 + 20000
        val sdkSandboxEnd = user * 100000 + 29999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tetherInterfaces = listOf("ap+", "swlan+", "softap+", "rndis+", "usb+", "p2p+", "ncm+", "wlan+")

        val reservedLanV4 = listOf(
            "0.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val rawHost = ConfigGenerator.cleanHost(settings.host)
        val effectiveHost = resolvedServerHost?.takeIf { it.isNotBlank() } ?: rawHost
        val hostIsIpv4 = isValidIpv4(effectiveHost)
        val effectiveIpMode = ConfigGenerator.resolveAutoIpMode(settings, effectiveHost)

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
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/accept_local 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/default/accept_local 2>/dev/null || true")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/accept_local; do echo 1 > \$f 2>/dev/null || true; done")
        commands.add("echo 1 > /proc/sys/net/ipv4/tcp_fwmark_accept 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/fwmark_reflect 2>/dev/null || true")
        commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")

        if (effectiveIpMode == IpMode.IPV4_ONLY) {
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true")
            commands.add("echo 1 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true")
            commands.add("for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 1 > \$f 2>/dev/null || true; done")
            commands.add("echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true")
        }
        commands.add("settings put global private_dns_mode off 2>/dev/null || true")
        commands.add("conntrack -F 2>/dev/null || true")

        // 2. Hotspot & LAN subnet return routing (Pref 8500..8503 — before app rules at 8800!)
        commands.add("ip rule add to 192.168.0.0/16 lookup local_network pref 8500 2>/dev/null || true")
        commands.add("ip rule add to 192.168.0.0/16 lookup main pref 8501 2>/dev/null || true")
        commands.add("ip rule add to 172.16.0.0/12 lookup local_network pref 8502 2>/dev/null || true")
        commands.add("ip rule add to 172.16.0.0/12 lookup main pref 8503 2>/dev/null || true")

        // 3. App Loopback Source Binding + Kernel __fib_validate_source() Unicast Rules (Table 2088)
        // - `uidrange $uRange lookup 2088 pref 8800` binds all target app sockets (Chrome, YouTube, etc.)
        //   to `src 127.0.0.1` on `dev lo`, so redirected packets are 127.0.0.1 -> 127.0.0.1 (0% WebRTC local IP leak!).
        // - `from 127.0.0.0/8 iif lo lookup 2088 pref 8805` and `to 198.18.0.0/15 iif lo lookup 2088 pref 8806`
        //   have NO uidrange selector, so when __fib_validate_source() runs on lo with uid = -1 (INVALID_UID)
        //   for SYN-ACK and UDP DNS/STUN replies, it gets RTN_UNICAST on dev lo instead of hitting Android's
        //   rule 32000 (unreachable)!
        commands.add("ip route replace default dev lo src 127.0.0.1 table $APP_ROUTE_TABLE_V4 2>/dev/null || true")
        for (uRange in uidRanges) {
            commands.add("ip rule add uidrange $uRange lookup $APP_ROUTE_TABLE_V4 pref $APP_ROUTE_PREF_V4 2>/dev/null || true")
        }
        commands.add("ip rule add from 127.0.0.0/8 iif lo lookup $APP_ROUTE_TABLE_V4 pref $LO_VALID_PREF_V4 2>/dev/null || true")
        commands.add("ip rule add to 198.18.0.0/15 iif lo lookup $APP_ROUTE_TABLE_V4 pref $FAKEIP_VALID_PREF_V4 2>/dev/null || true")
        commands.add("ip rule add from 10.0.0.0/8 iif lo lookup $APP_ROUTE_TABLE_V4 pref $CELL_VALID_PREF_BASE 2>/dev/null || true")
        commands.add("ip rule add from 100.64.0.0/10 iif lo lookup $APP_ROUTE_TABLE_V4 pref ${CELL_VALID_PREF_BASE + 1} 2>/dev/null || true")
        commands.add("ip rule add from 192.168.0.0/16 iif lo lookup $APP_ROUTE_TABLE_V4 pref ${CELL_VALID_PREF_BASE + 2} 2>/dev/null || true")
        commands.add("ip rule add from 172.16.0.0/12 iif lo lookup $APP_ROUTE_TABLE_V4 pref ${CELL_VALID_PREF_BASE + 3} 2>/dev/null || true")

        // 4. Always accept local redirected sockets on INPUT (both Phone OUTPUT->lo and Hotspot PREROUTING)
        commands.add("iptables -w -N $CHAIN_HS_IN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -i lo -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $inboundPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --sport $inboundPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $socksPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --sport $socksPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --sport $dnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $dnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --sport $dnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $stunPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --sport $stunPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $hsDnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $hsDnsPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p udp --dport $hsStunPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_HS_IN -p tcp --dport $hsInboundPort -j ACCEPT 2>/dev/null || true")
        commands.add("iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true")

        // 5. PHONE NAT OUTPUT (DNS -> 127.0.0.1:$dnsPort, STUN -> 127.0.0.1:$stunPort, TCP -> 127.0.0.1:$inboundPort)
        // IMPORTANT: Exempt `-d 127.0.0.0/8` (never `-s 127.0.0.0/8`!) so sockets bound to `src 127.0.0.1` via Table 2088
        // are properly redirected to sing-box and StunResponder!
        commands.add("iptables -w -t nat -N $CHAIN_NAT_V4 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --sport $inboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --sport $socksPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --sport $hsInboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --sport $dnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $hsDnsPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --sport $hsStunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -m addrtype --dst-type LOCAL -j RETURN 2>/dev/null || true")

        // Exempt sing-box's bootstrap DNS (9.9.9.9:53) and the upstream Proxy Server IP for root/system
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        if (hostIsIpv4) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $effectiveHost -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
        }

        // Redirect all Phone IPv4 DNS (UDP & TCP Port 53) into sing-box's 127.0.0.1:$dnsPort listener
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")

        // Fast-reject QUIC (UDP 80/443) and DoT/DoQ (853) so Chrome & netd stay on Port 53 FakeIP + TCP 443
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 80 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 443 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 853 -j REDIRECT --to-ports 9 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 853 -j RETURN 2>/dev/null || true")

        // Redirect Phone STUN/WebRTC UDP ports into StunResponder (127.0.0.1:$stunPort)
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 3478:3497 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 5349:5350 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp --dport 19300:19320 -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 3478:3497 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 5349:5350 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp --dport 19300:19320 -j RETURN 2>/dev/null || true")

        // System daemons (0-9999, including sing-box UID 0) bypass app TCP redirect
        commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        // Redirect FakeIP range (198.18.0.0/15) for target app UIDs before reservedLanV4 checks
        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -d 198.18.0.0/15 -m owner --uid-owner $uRange -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
        }

        for (range in reservedLanV4) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -d $range -j RETURN 2>/dev/null || true")
        }

        // Redirect all remaining public UDP from target apps to StunResponder (127.0.0.1:$stunPort)
        // and all TCP to sing-box (127.0.0.1:$inboundPort)
        for (uRange in uidRanges) {
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p udp -m owner --uid-owner $uRange -j REDIRECT --to-ports $stunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_NAT_V4 -p tcp -m owner --uid-owner $uRange -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        }
        commands.add("iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true")

        // 6. PHONE IPv4 FILTER SHIELD
        commands.add("iptables -w -N $CHAIN_FILTER_V4 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -o lo -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -d 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -s 127.0.0.0/8 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m addrtype --dst-type LOCAL -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m state --state ESTABLISHED,RELATED -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $stunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --sport $hsStunPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 9 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 853 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 3478:3497 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 5349:5350 -j DROP 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport 19300:19320 -j DROP 2>/dev/null || true")

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
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --dport $hsInboundPort -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp --sport $hsInboundPort -j RETURN 2>/dev/null || true")

        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p tcp -d ${ConfigGenerator.BOOTSTRAP_DNS_IPV4} --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true")

        if (hostIsIpv4) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -d $effectiveHost -j RETURN 2>/dev/null || true")
        }

        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 67:68 -j RETURN 2>/dev/null || true")
        commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp --dport 123 -j RETURN 2>/dev/null || true")
        for (range in reservedLanV4) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -d $range -j RETURN 2>/dev/null || true")
        }

        commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")

        for (uRange in uidRanges) {
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || iptables -w -A $CHAIN_FILTER_V4 -p udp -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
            commands.add("iptables -w -A $CHAIN_FILTER_V4 -m owner --uid-owner $uRange -j DROP 2>/dev/null || true")
        }
        commands.add("iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true")

        // 7. IPV6 FILTER SHIELD
        commands.add("ip6tables -w -N $CHAIN_FILTER_V6 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -o lo -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -d ::1/128 -j RETURN 2>/dev/null || true")
        commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -s ::1/128 -j RETURN 2>/dev/null || true")
        if (effectiveIpMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -w -A $CHAIN_FILTER_V6 -j DROP 2>/dev/null || true")
        }
        commands.add("ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true")

        // 8. HOTSPOT ROUTING & STUN RESPONDER (Dedicated AF_INET listeners $hsInboundPort, $hsDnsPort, $hsStunPort)
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
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 53 -j REDIRECT --to-ports $hsDnsPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp --dport 53 -j REDIRECT --to-ports $hsDnsPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 80 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 443 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 853 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp --dport 853 -j RETURN 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 3478:3497 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 5349:5350 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp --dport 19300:19320 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports $hsInboundPort 2>/dev/null || true")
            commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            for (range in reservedLanV4) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -d $range -j RETURN 2>/dev/null || true")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p tcp -j REDIRECT --to-ports $hsInboundPort 2>/dev/null || true")
                commands.add("iptables -w -t nat -A $CHAIN_HS_NAT -i $iface -p udp -j REDIRECT --to-ports $hsStunPort 2>/dev/null || true")
            }
            commands.add("iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true")

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

        // 9. Flush Android DNS caches once on tunnel startup
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
            "echo 1 > /proc/sys/net/ipv4/conf/all/accept_local 2>/dev/null || true",
            "for f in /proc/sys/net/ipv4/conf/*/accept_local; do echo 1 > \$f 2>/dev/null || true; done",
            "for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 1 > \$f 2>/dev/null || true; done",
            "echo 0 > /proc/sys/net/ipv6/conf/lo/disable_ipv6 2>/dev/null || true",
            "settings put global private_dns_mode off 2>/dev/null || true",
            "ip route replace default dev lo src 127.0.0.1 table $APP_ROUTE_TABLE_V4 2>/dev/null || true",
            "iptables -w -D INPUT -j $CHAIN_HS_IN 2>/dev/null; iptables -w -I INPUT 1 -j $CHAIN_HS_IN 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $CHAIN_FILTER_V4 2>/dev/null; iptables -w -I OUTPUT 1 -j $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $CHAIN_NAT_V4 2>/dev/null; iptables -w -t nat -I OUTPUT 1 -j $CHAIN_NAT_V4 2>/dev/null || true",
            "ip6tables -w -D OUTPUT -j $CHAIN_FILTER_V6 2>/dev/null; ip6tables -w -I OUTPUT 1 -j $CHAIN_FILTER_V6 2>/dev/null || true"
        )
        if (routeHotspot) {
            list.add("iptables -w -t nat -D PREROUTING -j $CHAIN_HS_NAT 2>/dev/null; iptables -w -t nat -I PREROUTING 1 -j $CHAIN_HS_NAT 2>/dev/null || true")
            list.add("iptables -w -D FORWARD -j $CHAIN_HS_FWD 2>/dev/null; iptables -w -I FORWARD 1 -j $CHAIN_HS_FWD 2>/dev/null || true")
        }
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
            "settings put global private_dns_mode opportunistic 2>/dev/null || true",
            "for p in 8500 8501 8502 8503 8504 8505 $APP_ROUTE_PREF_V4 $LO_VALID_PREF_V4 $FAKEIP_VALID_PREF_V4 31000 31001 31002 31003; do while ip rule del pref \$p 2>/dev/null; do :; done; done",
            "ip route flush table $APP_ROUTE_TABLE_V4 2>/dev/null || true",
            "while ip -6 rule del pref $APP_BLOCK_PREF_V6 2>/dev/null; do :; done",
            "ip -6 route flush table $APP_BLOCK_TABLE_V6 2>/dev/null || true",
            "iptables -w -D OUTPUT -j $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -F $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -X $CHAIN_FILTER_V4 2>/dev/null || true",
            "iptables -w -t nat -D OUTPUT -j $CHAIN_NAT_V4 2>/dev/null || true",
            "iptables -w -t nat -F $CHAIN_NAT_V4 2>/dev/null || true",
            "iptables -w -t nat -X $CHAIN_NAT_V4 2>/dev/null || true",
            "iptables -w -t nat -D POSTROUTING -j $CHAIN_POST_V4 2>/dev/null || true",
            "iptables -w -t nat -F $CHAIN_POST_V4 2>/dev/null || true",
            "iptables -w -t nat -X $CHAIN_POST_V4 2>/dev/null || true",
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
