package com.nameless.proxy.core

object IptablesManager {

    private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})(/\\d{1,2})?$")

    private fun isValidIpv4(host: String): Boolean = IPV4_REGEX.matches(host.trim())

    fun generateEnableCommands(
        inboundPort: Int,
        settings: ProxySettings,
        selectedUids: List<Int>? = null,
        relayHost: String = ""
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
        val chainHotspotNat = "NAMELESS_HS_NAT_$key"
        val chainHotspotMangle = "NAMELESS_HS_MANGLE_$key"
        val chainHotspotFwd = "NAMELESS_HS_FWD_$key"
        val chainHotspotTtl = "NAMELESS_HS_TTL_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val appStart = user * 100000 + 10000
        val appEnd = user * 100000 + 19999
        val isolatedStart = user * 100000 + 90000
        val isolatedEnd = user * 100000 + 99999

        val tableId = 2080 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + (user * 0x10) + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        val tetherInterfaces = listOf("wlan+", "ap+", "rndis+", "usb+", "softap+", "swlan+", "wigig+")

        val reservedV4 = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
        )

        val hostIsIpv4 = isValidIpv4(settings.host)
        val relayIsIpv4 = isValidIpv4(relayHost)
        val commands = mutableListOf<String>()

        // 1. Flush existing rules for this user & slot
        commands.addAll(generateDisableCommands(user, slot))

        // 2. Safe Loopback & Kernel TCP BBR + FastOpen Turbo
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null")
        commands.add("echo 0 > /proc/sys/net/ipv4/conf/lo/rp_filter 2>/dev/null")
        commands.add("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null")

        if (settings.enableBbrAndTfo) {
            commands.add("sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null || true")
            commands.add("echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null || true")
        }

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

        // 4. PHONE OUTBOUND MANGLE (100% UDP 53 DNS + App UDP TPROXY)
        commands.add("iptables -t mangle -N $chainOutMangle")
        if (hostIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d ${settings.host.trim()} -j RETURN")
        }
        if (relayIsIpv4) {
            commands.add("iptables -t mangle -A $chainOutMangle -d ${relayHost.trim()} -j RETURN")
        }

        if (!hostIsIpv4 || (relayHost.isNotBlank() && !relayIsIpv4)) {
            commands.add("iptables -t mangle -A $chainOutMangle -p udp -d 8.8.8.8 --dport 53 -m owner --uid-owner 0 -j RETURN")
        }
        commands.add("iptables -t mangle -A $chainOutMangle -p udp --dport 53 -j MARK --set-mark $markHex")

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
            }
        }
        commands.add("iptables -t mangle -I OUTPUT 1 -j $chainOutMangle")

        // 5. PHONE WEBRTC, QUIC-DNS, KILL-SWITCH & STALE TCP SOCKET RESET SHIELD
        commands.add("iptables -N $chainFilter 2>/dev/null")
        commands.add("iptables -A $chainFilter -p udp --dport 53 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 853 -j DROP")
        commands.add("iptables -A $chainFilter -p udp -d 8.8.8.8 --dport 443 -j DROP")
        commands.add("iptables -A $chainFilter -p udp -d 8.8.4.4 --dport 443 -j DROP")
        commands.add("iptables -A $chainFilter -p udp -d 1.1.1.1 --dport 443 -j DROP")
        commands.add("iptables -A $chainFilter -p udp -d 1.0.0.1 --dport 443 -j DROP")
        commands.add("iptables -A $chainFilter -m owner --uid-owner 0-9999 -j RETURN")
        commands.add("iptables -A $chainFilter -p udp --dport 3478 -j DROP")

        if (settings.enableKillSwitch) {
            // Strict Kill-Switch: Only allow loopback (127.0.0.0/8) where REDIRECTed TCP goes
            commands.add("iptables -A $chainFilter -d 127.0.0.0/8 -j RETURN")
        } else {
            for (range in reservedV4) {
                commands.add("iptables -A $chainFilter -d $range -j RETURN")
            }
        }
        if (hostIsIpv4) {
            commands.add("iptables -A $chainFilter -d ${settings.host.trim()} -j RETURN")
        }
        if (relayIsIpv4) {
            commands.add("iptables -A $chainFilter -d ${relayHost.trim()} -j RETURN")
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
        }
        commands.add("iptables -I OUTPUT 1 -j $chainFilter")

        // 6. PHONE LOCAL TCP, DNS (53) & PRIVATE DNS DoT (853) REDIRECTION
        commands.add("iptables -t nat -N $chainNatV4")
        if (hostIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${settings.host.trim()} -j RETURN")
        }
        if (relayIsIpv4) {
            commands.add("iptables -t nat -A $chainNatV4 -d ${relayHost.trim()} -j RETURN")
        }
        commands.add("iptables -t nat -A $chainNatV4 -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
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
        }
        commands.add("iptables -t nat -I OUTPUT 1 -j $chainNatV4")

        // 7. PHONE IPV6 LEAK & PRIVATE DNS IPV6 FALLBACK SHIELD
        if (settings.ipMode == IpMode.IPV4_ONLY) {
            commands.add("ip6tables -N $chainV6Filter 2>/dev/null")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp --dport 853 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || ip6tables -A $chainV6Filter -p tcp --dport 53 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p udp --dport 53 -j DROP")
            commands.add("ip6tables -A $chainV6Filter -p udp --dport 853 -j DROP")
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
            commands.add("ip6tables -t nat -A $chainNatV6 -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort")
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

        // 8. HOTSPOT / TETHERING ROUTING + CARRIER TTL=64 HIDE (Owner User 0)
        if (settings.routeHotspot && user == 0) {
            commands.add("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null")
            val tetherIfs = listOf("wlan0", "wlan1", "wlan2", "ap0", "ap1", "rndis0", "usb0", "softap0", "swlan0")
            for (tif in tetherIfs) {
                commands.add("echo 0 > /proc/sys/net/ipv4/conf/$tif/rp_filter 2>/dev/null")
            }
            commands.add("ip rule add fwmark $hsMarkHex table $hsTableId pref 500")
            commands.add("ip route add local 0.0.0.0/0 dev lo table $hsTableId")
            commands.add("iptables -I FORWARD 1 -j ACCEPT 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 192.168.0.0/16 -j MASQUERADE 2>/dev/null")
            commands.add("iptables -t nat -I POSTROUTING 1 -s 172.16.0.0/12 -j MASQUERADE 2>/dev/null")

            // Carrier Hotspot / Tethering Detection Hide (Mangles TTL/HL to 64)
            if (settings.hideHotspotTtl) {
                commands.add("iptables -t mangle -N $chainHotspotTtl 2>/dev/null")
                commands.add("iptables -t mangle -A $chainHotspotTtl -j TTL --ttl-set 64 2>/dev/null || true")
                commands.add("iptables -t mangle -I POSTROUTING 1 -j $chainHotspotTtl 2>/dev/null || true")
            }

            commands.add("ip6tables -N $chainHotspotV6Block 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("ip6tables -A $chainHotspotV6Block -i $iface -j DROP")
            }
            commands.add("ip6tables -I FORWARD 1 -j $chainHotspotV6Block")

            commands.add("iptables -t nat -N $chainHotspotNat")
            if (hostIsIpv4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d ${settings.host.trim()} -j RETURN")
            }
            if (relayIsIpv4) {
                commands.add("iptables -t nat -A $chainHotspotNat -d ${relayHost.trim()} -j RETURN")
            }
            for (iface in tetherInterfaces) {
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp --dport 53 -j REDIRECT --to-ports $inboundPort")
                commands.add("iptables -t nat -A $chainHotspotNat -i $iface -p tcp --dport 853 -j REDIRECT --to-ports $inboundPort")
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
            for (iface in tetherInterfaces) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp --dport 53 -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
            }
            for (range in reservedV4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d $range -j RETURN")
            }
            if (hostIsIpv4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${settings.host.trim()} -j RETURN")
            }
            if (relayIsIpv4) {
                commands.add("iptables -t mangle -A $chainHotspotMangle -d ${relayHost.trim()} -j RETURN")
            }
            if (settings.transportMode == TransportMode.TCP_AND_UDP) {
                for (iface in tetherInterfaces) {
                    commands.add("iptables -t mangle -A $chainHotspotMangle -i $iface -p udp -j TPROXY --on-port $tproxyPort --tproxy-mark $hsMarkHex")
                }
            }
            commands.add("iptables -t mangle -I PREROUTING 1 -j $chainHotspotMangle")

            commands.add("iptables -N $chainHotspotFwd 2>/dev/null")
            for (iface in tetherInterfaces) {
                commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 3478 -j DROP")
                if (settings.transportMode == TransportMode.TCP_ONLY) {
                    commands.add("iptables -A $chainHotspotFwd -i $iface -p udp --dport 443 -j DROP")
                }
            }
            commands.add("iptables -I FORWARD 1 -j $chainHotspotFwd")
        }

        // 9. Destroy pre-existing un-tunneled HTTP/HTTPS/DNS sockets and flush DNS cache
        commands.add("ss -K dport = :80 2>/dev/null || true")
        commands.add("ss -K dport = :443 2>/dev/null || true")
        commands.add("ss -K dport = :853 2>/dev/null || true")
        commands.add("ss -K dport = :53 2>/dev/null || true")
        commands.add("conntrack -F 2>/dev/null || true")
        commands.add("ndc resolver clearnetdns wlan0 2>/dev/null || true")
        commands.add("ndc resolver clearnetdns rmnet_data0 2>/dev/null || true")
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
        val chainHotspotTtl = "NAMELESS_HS_TTL_$key"
        val chainHotspotV6Block = "NAMELESS_HS_V6_$key"

        val tableId = 2080 + (user * 10) + slot
        val markHex = "0x" + Integer.toHexString(0x1080 + (user * 0x10) + slot)
        val hsTableId = 2000 + slot
        val hsMarkHex = "0x" + Integer.toHexString(0x1000 + slot)

        return listOf(
            "iptables -t mangle -D POSTROUTING -j $chainHotspotTtl 2>/dev/null",
            "iptables -t mangle -F $chainHotspotTtl 2>/dev/null",
            "iptables -t mangle -X $chainHotspotTtl 2>/dev/null",
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
