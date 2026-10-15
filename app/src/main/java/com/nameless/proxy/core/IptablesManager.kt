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

        val dnsPort = inboundPort + 2
        val tproxyPort = inboundPort + 8
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

        val tableId = 2080 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + (user * 0x10) + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "swlan+", "wigig+", "p2p+", "ncm+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24",
            "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val hostIsIpv4 = isValidIpv4(settings.host)
        val commands = mutableListOf<String>()

        // 1. Flush existing rules for this user & slot
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Disable rp_filter across ALL interfaces (Critical for Cellular rmnet_data0 TPROXY!)
        commands.add("for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > \"\$f\" 2>/dev/null; done")
        commands.add("for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > \"\$f\" 2>/dev/null; done")

        // 3. Policy Routing for TPROXY
        commands.add("ip rule add fwmark $markHex table $tableId pref ${100 + user}")
        commands.add("ip route add local 0.0.0.0/0 dev lo table $tableId")
        commands.add("ip -6 rule add fwmark $markHex table $tableId pref ${100 + user} 2>/dev/null || true")
        commands.add("ip -6 route add local ::/0 dev lo table $tableId 2>/dev/null || true")

        // Ingress TPROXY Mangle for Phone (IPv4)
        commands.add("iptables -t mangle -N $chainPreMangle")
        commands.add("iptables -t mangle -A $chainPreMangle -p tcp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -A $chainPreMangle -p udp -m mark --mark $markHex -j TPROXY --on-port $tproxyPort --tproxy-mark $markHex")
        commands.add("iptables -t mangle -I PREROUTING 1 -j $chainPreMangle")

        // 4. PHONE OUTBOUND MANGLE (App UDP TPROXY for WebRTC STUN)
        commands.add("iptables -t mangle -N $chainOutMangle")
        if (hostIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d ${settings.host.trim()} -j RETURN")
        }
        // Let NAT OUTPUT handle UDP port 53 to dnsPort; skip UDP 443 QUIC
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 443 -j RETURN")
        commands.add("iptables -t mangle -A $chainOutMangle -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d $range -j RETURN")
        }

        if (settings.transportMode == TransportMode.TCP_AND_UDP) {
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

        // 5. PHONE WEBRTC, QUIC-BLOCK, KILL-SWITCH & STALE TCP SOCKET RESET SHIELD
        commands.add("iptables -N $chainFilter 2>/dev/null")
        // CRITICAL: Explicitly allow all packets already redirected by chainNatV4 to sing-box ports!
        // (On Cellular rmnet_data0, REDIRECT sets dst to the cellular IP, so matching by port guarantees 100% pass-through!)
        commands.add("iptables -A $chainFilter -o lo -j RETURN")
        commands.add("iptables -A $chainFilter -d 127.0.0.0/8 -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $inboundPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $dnsPort -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport $tproxyPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport $tproxyPort -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")
        commands.add("iptables -A $chainFilter -p tcp --dport 53 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 853 -j DROP")
        // Block UDP 443 (QUIC / HTTP/3) so Chrome & browsers always use tunneled TCP 443
        commands.add("iptables -A $chainFilter -p udp --dport 443 -j DROP")
        commands.add("iptables -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 3478 -j DROP")

        if (!settings.enableKillSwitch) {
            for (range in reservedV4) {
                commands.add("iptables -A $chainFilter -d $range -j RETURN")
            }
        }
        if (hostIsIpv4) {
            commands.add("iptables -A $chainFilter -d ${settings.host.trim()} -j RETURN")
        }

        if (settings.transportMode == TransportMode.TCP_AND_UDP) {
            commands.add("iptables -A $chainFilter -p udp -m mark --mark $markHex -j RETURN")
        }

        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -A $chainFilter -p tcp -m owner --uid-owner $appStart-$appEnd -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp -m owner --uid-owner $appStart-$appEnd -j DROP")
            commands.add("iptables -A $chainFilter -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $appStart-$appEnd -j DROP")
            commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            if (settings.enableKillSwitch) {
                commands.add("iptables -A $chainFilter -m owner --uid-owner $appStart-$isolatedEnd -j DROP")
            }
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -A $chainFilter -p tcp -m owner --uid-owner $uid -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp -m owner --uid-owner $uid -j DROP")
                commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $uid -j DROP")
                if (settings.enableKillSwitch) {
                    commands.add("iptables -A $chainFilter -m owner --uid-owner $uid -j DROP")
                }
            }
            commands.add("iptables -A $chainFilter -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainFilter -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            commands.add("iptables -A $chainFilter -p udp -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
        }
        commands.add("iptables -I OUTPUT 1 -j $chainFilter")

        // 6. PHONE LOCAL TCP + NAT UDP/TCP DNS (53) + DoT (853) REDIRECTION
        commands.add("iptables -t nat -N $chainNatV4")
        if (hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host.trim()} -j RETURN")
        }
        if (!hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -p udp -d 8.8.8.8 --dport 53 -m owner --uid-owner 0 -j RETURN")
        }
        // Redirect ALL UDP & TCP port 53 (including UID 0 netd, UID 1051, and app UIDs) to dns-in (dnsPort)
        commands.add("iptables -t nat -A $chainNatV4 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort")
        commands.add("iptables -t nat -A $chainNatV4 -m owner --uid-owner 0-9999 -j RETURN")

        for (range in reservedV4) {
            commands.add("iptables -t nat -A $chainNatV4 -d $range -j RETURN")
        }

        if (selectedUids.isNullOrEmpty()) {
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $appStart-$appEnd -j REDIRECT --to-ports $inboundPort")
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort")
        } else {
            for (uid in selectedUids) {
                commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -t nat -A $chainNatV4 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort")
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 7. PHONE IPV6 DNS REDIRECTION & IPV6 LEAK SHIELD (Critical for Cellular VoLTE/LTE!)
        // Redirect IPv6 UDP/TCP port 53 to sing-box's dual-stack dns-in (dnsPort) if kernel supports ip6tables nat
        commands.add("ip6tables -t nat -N $chainNatV6 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || true")
        commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
        if (settings.ipMode != IpMode.IPV4_ONLY) {
            commands.add("ip6tables -t nat -A $chainNatV6 -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t nat -A $chainNatV6 -d ::1/128 -j RETURN 2>/dev/null || true")
            commands.add("ip6tables -t nat -A $chainNatV6 -d fe80::/10 -j RETURN 2>/dev/null || true")
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $appStart-$appEnd -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
                }
                commands.add("ip6tables -t nat -A $chainNatV6 -p tcp -m owner --uid-owner $isolatedStart-$isolatedEnd -j REDIRECT --to-ports $inboundPort 2>/dev/null || true")
            }
        }
        commands.add("ip6tables -t nat -I OUTPUT 1 -j $chainNatV6 2>/dev/null || true")

        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -N $chainV6Filter 2>/dev/null")
            // Allow redirected IPv6 DNS packets to reach sing-box's local dual-stack dns-in (dnsPort)
            commands.add("ip6tables -A $chainV6Filter -o lo -j RETURN")
            commands.add("ip6tables -A $chainV6Filter -d ::1/128 -j RETURN")
            commands.add("ip6tables -A $chainV6Filter -p udp --dport $dnsPort -j RETURN")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport $dnsPort -j RETURN")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport $inboundPort -j RETURN")
            // If kernel did NOT support ip6tables -t nat, reject un-redirected IPv6 DNS immediately so netd falls back to IPv4 in 0ms
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp --dport 853 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp --dport 53 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p udp --dport 53 -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -p udp --dport 53 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p udp --dport 853 -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -p udp --dport 853 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner 0-9999 -j RETURN")
            if (selectedUids.isNullOrEmpty()) {
                commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $appStart-$appEnd -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -m owner --uid-owner $appStart-$appEnd -j DROP")
                commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $isolatedStart-$isolatedEnd -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            } else {
                for (uid in selectedUids) {
                    commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $uid -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -m owner --uid-owner $uid -j DROP")
                }
                commands.add("ip6tables -A $chainV6Filter -m owner --uid-owner $isolatedStart-$isolatedEnd -j REJECT 2>/dev/null || ip6tables -A $chainV6Filter -m owner --uid-owner $isolatedStart-$isolatedEnd -j DROP")
            }
            commands.add("ip6tables -I OUTPUT 1 -j $chainV6Filter")
        }

        // 8. ZERO-LEAK HOTSPOT / TETHERING ROUTING
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")

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
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort")
            }
            for (subnet in listOf("192.168.0.0/16", "172.16.0.0/12", "10.0.0.0/8")) {
                commands.add("iptables -t nat -A $chainHotspotNat -s $subnet -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainHotspotNat -s $subnet -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort")
                commands.add("iptables -t nat -A $chainHotspotNat -s $subnet -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort")
            }
            for (range in reservedV4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d $range -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            for (subnet in listOf("192.168.0.0/16", "172.16.0.0/12", "10.0.0.0/8")) {
                commands.add("iptables -t nat -A $chainHotspotNat -s $subnet -p tcp -j REDIRECT --to-ports $inboundPort")
            }
            commands.add("iptables -t nat -I PREROUTING 1 -j $chainHotspotNat")

            commands.add("iptables -t mangle -N $chainHotspotMangle")
            commands.add("iptables -t mangle -A $chainHotspotMangle -i lo -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 53 -j RETURN")
            commands.add("iptables -t mangle -A $chainHotspotMangle -p udp --dport 443 -j RETURN")
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (hostIsIpv4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host.trim()} -j RETURN")
            }
            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
            }
            commands.add("iptables -t mangle -I PREROUTING 1 -j $chainHotspotMangle")

            commands.add("iptables -N $chainHotspotFwd 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("iptables -A $chainHotspotFwd -i $iface -p tcp -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainHotspotFwd -i $iface -p tcp -j DROP")
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp -j DROP")
            }
            for (subnet in listOf("192.168.0.0/16", "172.16.0.0/12")) {
                commands.add("iptables -A $chainHotspotFwd -s $subnet -p tcp -j REJECT --reject-with tcp-reset 2>/dev/null || iptables -A $chainHotspotFwd -s $subnet -p tcp -j DROP")
                commands.add("iptables -A $chainHotspotFwd -s $subnet -p udp -j DROP")
            }
            commands.add("iptables -I FORWARD 1 -j $chainHotspotFwd")
        }

        // 9. Destroy pre-existing un-tunneled HTTP/HTTPS/DoT sockets and flush DNS cache
        // (IMPORTANT: Never call 'ndc resolver clearnetdns' because that deletes the DNS servers from rmnet_data0!)
        commands.add("ss -K dport = :80 2>/dev/null || true")
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

        val tableId = 2080 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + (user * 0x10) + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

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
