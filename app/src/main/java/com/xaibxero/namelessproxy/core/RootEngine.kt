package com.xaibxero.namelessproxy.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootEngine {

    data class CmdResult(val ok: Boolean, val out: String, val err: String)

    suspend fun su(cmd: String): CmdResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", cmd).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            CmdResult(code == 0, out, err)
        } catch (e: Exception) {
            CmdResult(false, "", e.message ?: "su execution failed")
        }
    }

    suspend fun isRootAvailable(): Boolean = su("id -u").out == "0"

    suspend fun ensureEnvironment(context: Context): Boolean {
        val nativeBin = "${context.applicationInfo.nativeLibraryDir}/libsingbox.so"
        val userDir = ProfileContext.userDir
        val setup = """
            mkdir -p '${ProfileContext.BASE_DIR}/bin' '$userDir'
            chmod 700 '${ProfileContext.BASE_DIR}' '$userDir'
            if [ ! -f '${ProfileContext.BIN_PATH}' ] || [ "$nativeBin" -nt '${ProfileContext.BIN_PATH}' ]; then
                cp -f "$nativeBin" '${ProfileContext.BIN_PATH}'
                chmod 755 '${ProfileContext.BIN_PATH}'
            fi
        """.trimIndent()
        return su(setup).ok
    }

    suspend fun startProxy(context: Context, slot: ProxySlot, settings: ProfileSettings): CmdResult {
        if (slot.host.isBlank() || slot.port !in 1..65535) {
            return CmdResult(false, "", "Enter a valid proxy IP/host and port")
        }
        if (!isRootAvailable()) {
            return CmdResult(false, "", "Root (su) permission denied")
        }
        if (!ensureEnvironment(context)) {
            return CmdResult(false, "", "Failed to prepare /data/adb/namelessproxy")
        }

        val configJson = SingBoxConfigBuilder.build(slot, settings)
        val daemonScript = buildLiveDaemonScript(slot, settings)

        val tmpConfig = File(context.cacheDir, "config_${ProfileContext.userId}.json").apply {
            writeText(configJson)
        }
        val tmpDaemon = File(context.cacheDir, "daemon_${ProfileContext.userId}.sh").apply {
            writeText(daemonScript)
        }

        val bootCmd = if (settings.bootOnStartup) {
            "touch '${ProfileContext.bootFlagPath}'"
        } else {
            "rm -f '${ProfileContext.bootFlagPath}'"
        }

        val startCmd = """
            cp -f '${tmpConfig.absolutePath}' '${ProfileContext.configPath}'
            cp -f '${tmpDaemon.absolutePath}' '${ProfileContext.daemonScriptPath}'
            chmod 600 '${ProfileContext.configPath}'
            chmod 755 '${ProfileContext.daemonScriptPath}'
            $bootCmd
            touch '${ProfileContext.enabledFlagPath}'
            pkill -f 'live_daemon.sh.*user${ProfileContext.userId}' 2>/dev/null || true
            pkill -f 'sing-box.*user${ProfileContext.userId}' 2>/dev/null || true
            setsid sh '${ProfileContext.daemonScriptPath}' >/dev/null 2>&1 < /dev/null &
            sleep 0.7
            pgrep -f 'sing-box.*user${ProfileContext.userId}' >/dev/null && echo "RUNNING"
        """.trimIndent()

        val res = su(startCmd)
        tmpConfig.delete()
        tmpDaemon.delete()
        return res.copy(ok = res.out.contains("RUNNING"))
    }

    suspend fun stopProxy(): CmdResult {
        val u = ProfileContext.userId
        val cmd = """
            rm -f '${ProfileContext.enabledFlagPath}'
            pkill -f 'live_daemon.sh.*user$u' 2>/dev/null || true
            pkill -f 'sing-box.*user$u' 2>/dev/null || true
            ${buildCleanupRulesSnippet()}
            echo "STOPPED"
        """.trimIndent()
        return su(cmd)
    }

    suspend fun isRunning(): Boolean {
        val res = su("pgrep -f 'sing-box.*user${ProfileContext.userId}' >/dev/null && echo YES")
        return res.out == "YES"
    }

    private fun buildLiveDaemonScript(slot: ProxySlot, settings: ProfileSettings): String {
        val u = ProfileContext.userId
        val chain = "NP_U$u"
        val uidRange = "${ProfileContext.uidStart}-${ProfileContext.uidEnd}"
        val bypassHex = "0x%x".format(ProfileContext.outboundBypassMark)
        val tproxyPort = ProfileContext.tproxyPort
        val dnsPort = ProfileContext.dnsPort
        val table = ProfileContext.routingTable
        val mark = ProfileContext.tproxyMark
        val blockIpv6 = settings.ipv6Mode == Ipv6Mode.STRICT_BLOCK
        val hotspot = settings.shareHotspot
        val tcpOnlyProxy = slot.protocol != ProxyProtocol.SOCKS5

        return """
            #!/system/bin/sh
            # Escape Android app cgroup so low-RAM devices cannot kill this daemon
            echo $$ > /dev/cpuset/tasks 2>/dev/null || true
            echo $$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true

            apply_rules() {
                ip rule show | grep -q "fwmark $mark lookup $table" || ip rule add fwmark $mark table $table pref $((1000 + $u))
                ip route replace local 0.0.0.0/0 dev lo table $table

                iptables -t mangle -N $chain 2>/dev/null || iptables -t mangle -F $chain
                iptables -t mangle -A $chain -m mark --mark $bypassHex -j RETURN
                iptables -t mangle -A $chain -d 127.0.0.0/8 -j RETURN
                iptables -t mangle -A $chain -d 10.0.0.0/8 -j RETURN
                iptables -t mangle -A $chain -d 172.16.0.0/12 -j RETURN
                iptables -t mangle -A $chain -d 192.168.0.0/16 -j RETURN
                iptables -t mangle -A $chain -d 169.254.0.0/16 -j RETURN
                iptables -t mangle -A $chain -d 224.0.0.0/4 -j RETURN
                iptables -t mangle -A $chain -p tcp -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark
                iptables -t mangle -A $chain -p udp -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark

                iptables -t mangle -C PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || \
                    iptables -t mangle -I PREROUTING 1 -m mark --mark $mark -j $chain

                iptables -t mangle -N ${chain}_OUT 2>/dev/null || iptables -t mangle -F ${chain}_OUT
                iptables -t mangle -A ${chain}_OUT -m mark --mark $bypassHex -j RETURN
                iptables -t mangle -A ${chain}_OUT -d 127.0.0.0/8 -j RETURN
                iptables -t mangle -A ${chain}_OUT -d 192.168.0.0/16 -j RETURN
                iptables -t mangle -A ${chain}_OUT -p tcp -j MARK --set-mark $mark
                iptables -t mangle -A ${chain}_OUT -p udp -j MARK --set-mark $mark
                iptables -t mangle -C OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || \
                    iptables -t mangle -I OUTPUT 1 -m owner --uid-owner $uidRange -j ${chain}_OUT

                # Capture Android Private DNS (netd UID 0-9999 on DoT 853 & DoH3 443) so even cloned profiles never leak
                iptables -t mangle -N ${chain}_PDNS 2>/dev/null || iptables -t mangle -F ${chain}_PDNS
                iptables -t mangle -A ${chain}_PDNS -m mark --mark $bypassHex -j RETURN
                iptables -t mangle -A ${chain}_PDNS -p tcp --dport 853 -j MARK --set-mark $mark
                iptables -t mangle -A ${chain}_PDNS -p udp --dport 853 -j MARK --set-mark $mark
                for DNS_IP in 8.8.8.8 8.8.4.4 1.1.1.1 1.0.0.1 9.9.9.9 149.112.112.112; do
                    iptables -t mangle -A ${chain}_PDNS -d ${'$'}DNS_IP -p udp --dport 443 -j MARK --set-mark $mark
                done
                iptables -t mangle -C OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || \
                    iptables -t mangle -I OUTPUT 1 -m owner --uid-owner 0-9999 -j ${chain}_PDNS

                # Force TCP-853 DoT fallback when upstream proxy (HTTP/HTTPS/SOCKS4) does not support UDP
                if [ "${if (tcpOnlyProxy) "1" else "0"}" = "1" ]; then
                    iptables -N ${chain}_UDPDROP 2>/dev/null || iptables -F ${chain}_UDPDROP
                    iptables -A ${chain}_UDPDROP -m mark --mark $bypassHex -j RETURN
                    iptables -A ${chain}_UDPDROP -p udp --dport 853 -j DROP
                    for DNS_IP in 8.8.8.8 8.8.4.4 1.1.1.1 1.0.0.1 9.9.9.9 149.112.112.112; do
                        iptables -A ${chain}_UDPDROP -d ${'$'}DNS_IP -p udp --dport 443 -j DROP
                    done
                    iptables -C OUTPUT -j ${chain}_UDPDROP 2>/dev/null || \
                        iptables -I OUTPUT 1 -j ${chain}_UDPDROP
                fi

                # Hard-capture standard port 53 DNS
                iptables -t nat -N ${chain}_DNS 2>/dev/null || iptables -t nat -F ${chain}_DNS
                iptables -t nat -A ${chain}_DNS -m mark --mark $bypassHex -j RETURN
                iptables -t nat -A ${chain}_DNS -p udp --dport 53 -j REDIRECT --to-ports $dnsPort
                iptables -t nat -A ${chain}_DNS -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort
                iptables -t nat -C OUTPUT -m owner --uid-owner $uidRange -j ${chain}_DNS 2>/dev/null || \
                    iptables -t nat -I OUTPUT 1 -m owner --uid-owner $uidRange -j ${chain}_DNS

                if [ "${if (hotspot) "1" else "0"}" = "1" ]; then
                    echo 1 > /proc/sys/net/ipv4/ip_forward
                    for IFACE in wlan+ ap+ rndis+ swlan+; do
                        iptables -t mangle -C PREROUTING -i ${'$'}IFACE -j $chain 2>/dev/null || \
                            iptables -t mangle -A PREROUTING -i ${'$'}IFACE -j $chain
                        iptables -t nat -C PREROUTING -i ${'$'}IFACE -p udp --dport 53 -j REDIRECT --to-ports $dnsPort 2>/dev/null || \
                            iptables -t nat -A PREROUTING -i ${'$'}IFACE -p udp --dport 53 -j REDIRECT --to-ports $dnsPort
                    done
                fi

                if [ "${if (blockIpv6) "1" else "0"}" = "1" ]; then
                    ip6tables -C OUTPUT -m owner --uid-owner $uidRange ! -o lo -j DROP 2>/dev/null || \
                        ip6tables -I OUTPUT 1 -m owner --uid-owner $uidRange ! -o lo -j DROP
                    ip6tables -C OUTPUT -m owner --uid-owner 0-9999 -p tcp --dport 853 -j DROP 2>/dev/null || \
                        ip6tables -I OUTPUT 1 -m owner --uid-owner 0-9999 -p tcp --dport 853 -j DROP
                    ip6tables -C OUTPUT -m owner --uid-owner 0-9999 -p udp --dport 853 -j DROP 2>/dev/null || \
                        ip6tables -I OUTPUT 1 -m owner --uid-owner 0-9999 -p udp --dport 853 -j DROP
                else
                    ip -6 rule show | grep -q "fwmark $mark lookup $table" || ip -6 rule add fwmark $mark table $table pref $((1000 + $u))
                    ip -6 route replace local ::/0 dev lo table $table
                    ip6tables -t mangle -N $chain 2>/dev/null || ip6tables -t mangle -F $chain
                    ip6tables -t mangle -A $chain -m mark --mark $bypassHex -j RETURN
                    ip6tables -t mangle -A $chain -d ::1/128 -j RETURN
                    ip6tables -t mangle -A $chain -d fe80::/10 -j RETURN
                    ip6tables -t mangle -A $chain -d fc00::/7 -j RETURN
                    ip6tables -t mangle -A $chain -p tcp -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark
                    ip6tables -t mangle -A $chain -p udp -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark
                    ip6tables -t mangle -C PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || \
                        ip6tables -t mangle -I PREROUTING 1 -m mark --mark $mark -j $chain

                    ip6tables -t mangle -N ${chain}_OUT 2>/dev/null || ip6tables -t mangle -F ${chain}_OUT
                    ip6tables -t mangle -A ${chain}_OUT -m mark --mark $bypassHex -j RETURN
                    ip6tables -t mangle -A ${chain}_OUT -d ::1/128 -j RETURN
                    ip6tables -t mangle -A ${chain}_OUT -d fe80::/10 -j RETURN
                    ip6tables -t mangle -A ${chain}_OUT -p tcp -j MARK --set-mark $mark
                    ip6tables -t mangle -A ${chain}_OUT -p udp -j MARK --set-mark $mark
                    ip6tables -t mangle -C OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || \
                        ip6tables -t mangle -I OUTPUT 1 -m owner --uid-owner $uidRange -j ${chain}_OUT

                    # Route IPv6 Private DNS (DoT 853 & DoH3 443) through TProxy as well
                    ip6tables -t mangle -N ${chain}_PDNS 2>/dev/null || ip6tables -t mangle -F ${chain}_PDNS
                    ip6tables -t mangle -A ${chain}_PDNS -m mark --mark $bypassHex -j RETURN
                    ip6tables -t mangle -A ${chain}_PDNS -p tcp --dport 853 -j MARK --set-mark $mark
                    ip6tables -t mangle -A ${chain}_PDNS -p udp --dport 853 -j MARK --set-mark $mark
                    for DNS6_IP in 2001:4860:4860::8888 2001:4860:4860::8844 2606:4700:4700::1111 2606:4700:4700::1001 2620:fe::fe 2620:fe::9; do
                        ip6tables -t mangle -A ${chain}_PDNS -d ${'$'}DNS6_IP -p udp --dport 443 -j MARK --set-mark $mark
                    done
                    ip6tables -t mangle -C OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || \
                        ip6tables -t mangle -I OUTPUT 1 -m owner --uid-owner 0-9999 -j ${chain}_PDNS
                fi
            }

            while [ -f '${ProfileContext.enabledFlagPath}' ]; do
                if ! pgrep -f 'sing-box.*user$u' >/dev/null 2>&1; then
                    '${ProfileContext.BIN_PATH}' run -c '${ProfileContext.configPath}' > '${ProfileContext.userDir}/sing-box.log' 2>&1 &
                    sleep 0.5
                fi
                apply_rules
                sleep 3
            done
        """.trimIndent()
    }

    private fun buildCleanupRulesSnippet(): String {
        val u = ProfileContext.userId
        val chain = "NP_U$u"
        val uidRange = "${ProfileContext.uidStart}-${ProfileContext.uidEnd}"
        val table = ProfileContext.routingTable
        val mark = ProfileContext.tproxyMark
        return """
            iptables -t mangle -D OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || true
            iptables -t mangle -D OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || true
            iptables -t mangle -D PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || true
            iptables -t nat -D OUTPUT -m owner --uid-owner $uidRange -j ${chain}_DNS 2>/dev/null || true
            iptables -D OUTPUT -j ${chain}_UDPDROP 2>/dev/null || true
            for IFACE in wlan+ ap+ rndis+ swlan+; do
                iptables -t mangle -D PREROUTING -i ${'$'}IFACE -j $chain 2>/dev/null || true
                iptables -t nat -D PREROUTING -i ${'$'}IFACE -p udp --dport 53 -j REDIRECT --to-ports ${ProfileContext.dnsPort} 2>/dev/null || true
            done
            iptables -t mangle -F $chain 2>/dev/null && iptables -t mangle -X $chain 2>/dev/null || true
            iptables -t mangle -F ${chain}_OUT 2>/dev/null && iptables -t mangle -X ${chain}_OUT 2>/dev/null || true
            iptables -t mangle -F ${chain}_PDNS 2>/dev/null && iptables -t mangle -X ${chain}_PDNS 2>/dev/null || true
            iptables -t nat -F ${chain}_DNS 2>/dev/null && iptables -t nat -X ${chain}_DNS 2>/dev/null || true
            iptables -F ${chain}_UDPDROP 2>/dev/null && iptables -X ${chain}_UDPDROP 2>/dev/null || true
            ip rule del fwmark $mark table $table 2>/dev/null || true
            ip route flush table $table 2>/dev/null || true

            ip6tables -D OUTPUT -m owner --uid-owner $uidRange ! -o lo -j DROP 2>/dev/null || true
            ip6tables -D OUTPUT -m owner --uid-owner 0-9999 -p tcp --dport 853 -j DROP 2>/dev/null || true
            ip6tables -D OUTPUT -m owner --uid-owner 0-9999 -p udp --dport 853 -j DROP 2>/dev/null || true
            ip6tables -t mangle -D OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || true
            ip6tables -t mangle -D OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || true
            ip6tables -t mangle -D PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || true
            ip6tables -t mangle -F $chain 2>/dev/null && ip6tables -t mangle -X $chain 2>/dev/null || true
            ip6tables -t mangle -F ${chain}_OUT 2>/dev/null && ip6tables -t mangle -X ${chain}_OUT 2>/dev/null || true
            ip6tables -t mangle -F ${chain}_PDNS 2>/dev/null && ip6tables -t mangle -X ${chain}_PDNS 2>/dev/null || true
            ip -6 rule del fwmark $mark table $table 2>/dev/null || true
            ip -6 route flush table $table 2>/dev/null || true
        """.trimIndent()
    }
}
