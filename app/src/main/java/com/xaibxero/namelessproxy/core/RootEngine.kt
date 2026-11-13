package com.xaibxero.namelessproxy.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootEngine {

    data class CmdResult(val ok: Boolean, val out: String, val err: String)

    private var cachedSuFlag: String? = null

    suspend fun su(cmd: String): CmdResult = withContext(Dispatchers.IO) {
        val flag = cachedSuFlag
        if (flag != null) {
            return@withContext runSuWithFlag(flag, cmd)
        }
        val mmRes = runSuWithFlag("--mount-master", cmd)
        if (mmRes.ok) {
            cachedSuFlag = "--mount-master"
            return@withContext mmRes
        }
        val stdRes = runSuWithFlag("", cmd)
        if (stdRes.ok) {
            cachedSuFlag = ""
        }
        stdRes
    }

    private fun runSuWithFlag(flag: String, cmd: String): CmdResult = try {
        val args = if (flag.isNotBlank()) listOf("su", flag, "-c", cmd) else listOf("su", "-c", cmd)
        val process = ProcessBuilder(args).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        val err = process.errorStream.bufferedReader().readText().trim()
        val code = process.waitFor()
        CmdResult(code == 0, out, err)
    } catch (e: Exception) {
        CmdResult(false, "", e.message ?: "su execution failed")
    }

    suspend fun isRootAvailable(): Boolean = su("id -u").out == "0"

    private fun resolveBinarySnippet(nativeBin: String): String = """
        mkdir -p '${ProfileContext.BASE_DIR}/bin' '${ProfileContext.userDir}'
        chmod 700 '${ProfileContext.BASE_DIR}' '${ProfileContext.userDir}'
        if [ -f "$nativeBin" ]; then
            if [ ! -f '${ProfileContext.BIN_PATH}' ] || [ "$nativeBin" -nt '${ProfileContext.BIN_PATH}' ]; then
                cp -f "$nativeBin" '${ProfileContext.BIN_PATH}'
                chmod 755 '${ProfileContext.BIN_PATH}'
                chcon u:object_r:system_file:s0 '${ProfileContext.BIN_PATH}' 2>/dev/null || true
            fi
        fi
        ACTIVE_BIN='${ProfileContext.BIN_PATH}'
        if ! "${'$'}ACTIVE_BIN" version >/dev/null 2>&1; then
            mkdir -p '${ProfileContext.FALLBACK_BIN_DIR}'
            cp -f '${ProfileContext.BIN_PATH}' '${ProfileContext.FALLBACK_BIN_PATH}'
            chmod 755 '${ProfileContext.FALLBACK_BIN_PATH}'
            chcon u:object_r:system_file:s0 '${ProfileContext.FALLBACK_BIN_PATH}' 2>/dev/null || true
            ACTIVE_BIN='${ProfileContext.FALLBACK_BIN_PATH}'
        fi
    """.trimIndent()

    suspend fun startProxy(context: Context, slot: ProxySlot, settings: ProfileSettings): CmdResult {
        if (slot.host.isBlank() || slot.port !in 1..65535) {
            return CmdResult(false, "", "Enter a valid proxy IP/host and port")
        }
        if (!isRootAvailable()) {
            return CmdResult(false, "", "Root (su) permission denied")
        }

        val nativeBin = "${context.applicationInfo.nativeLibraryDir}/libsingbox.so"
        val u = ProfileContext.userId
        val configJson = SingBoxConfigBuilder.build(slot)
        val daemonScript = buildLiveDaemonScript(slot, settings)

        val tmpConfig = File(context.cacheDir, "config_$u.json").apply { writeText(configJson) }
        val tmpDaemon = File(context.cacheDir, "daemon_$u.sh").apply { writeText(daemonScript) }

        val bootCmd = if (settings.bootOnStartup) {
            "touch '${ProfileContext.bootFlagPath}'"
        } else {
            "rm -f '${ProfileContext.bootFlagPath}'"
        }

        val startCmd = """
            ${resolveBinarySnippet(nativeBin)}
            if ! "${'$'}ACTIVE_BIN" version >/dev/null 2>&1; then
                echo "ERR: sing-box binary could not execute (check arm64 ABI)" >&2
                exit 1
            fi
            cp -f '${tmpConfig.absolutePath}' '${ProfileContext.configPath}'
            cp -f '${tmpDaemon.absolutePath}' '${ProfileContext.daemonScriptPath}'
            chmod 600 '${ProfileContext.configPath}'
            chmod 755 '${ProfileContext.daemonScriptPath}'
            $bootCmd

            rm -f '${ProfileContext.enabledFlagPath}'
            if [ -f '${ProfileContext.daemonPidPath}' ]; then
                OLD_D=$(cat '${ProfileContext.daemonPidPath}' 2>/dev/null)
                [ -n "${'$'}OLD_D" ] && kill -9 "${'$'}OLD_D" 2>/dev/null || true
                rm -f '${ProfileContext.daemonPidPath}'
            fi
            if [ -f '${ProfileContext.singboxPidPath}' ]; then
                OLD_S=$(cat '${ProfileContext.singboxPidPath}' 2>/dev/null)
                [ -n "${'$'}OLD_S" ] && kill -9 "${'$'}OLD_S" 2>/dev/null || true
                rm -f '${ProfileContext.singboxPidPath}'
            fi
            ${buildCleanupRulesSnippet()}

            CHECK_OUT=$("${'$'}ACTIVE_BIN" check -c '${ProfileContext.configPath}' 2>&1)
            if [ ${'$'}? -ne 0 ]; then
                echo "ERR: ${'$'}CHECK_OUT" >&2
                exit 1
            fi

            : > '${ProfileContext.singboxLogPath}'
            touch '${ProfileContext.enabledFlagPath}'
            setsid sh '${ProfileContext.daemonScriptPath}' >/dev/null 2>&1 < /dev/null &
            sleep 1
            if [ -f '${ProfileContext.singboxPidPath}' ] && kill -0 "$(cat '${ProfileContext.singboxPidPath}' 2>/dev/null)" 2>/dev/null; then
                echo "RUNNING"
            else
                LOG_TAIL=$(tail -n 3 '${ProfileContext.singboxLogPath}' 2>/dev/null | tr '\n' ' ')
                echo "ERR: ${'$'}{LOG_TAIL:-sing-box exited immediately}" >&2
                exit 1
            fi
        """.trimIndent()

        val res = su(startCmd)
        tmpConfig.delete()
        tmpDaemon.delete()
        val cleanErr = res.err.removePrefix("ERR:").trim()
        return res.copy(ok = res.out.contains("RUNNING"), err = cleanErr)
    }

    suspend fun stopProxy(): CmdResult {
        // Box-for-Magisk order: remove iptables rules BEFORE killing sing-box
        val cmd = """
            rm -f '${ProfileContext.enabledFlagPath}'
            ${buildCleanupRulesSnippet()}
            if [ -f '${ProfileContext.daemonPidPath}' ]; then
                OLD_D=$(cat '${ProfileContext.daemonPidPath}' 2>/dev/null)
                [ -n "${'$'}OLD_D" ] && kill -9 "${'$'}OLD_D" 2>/dev/null || true
                rm -f '${ProfileContext.daemonPidPath}'
            fi
            if [ -f '${ProfileContext.singboxPidPath}' ]; then
                OLD_S=$(cat '${ProfileContext.singboxPidPath}' 2>/dev/null)
                [ -n "${'$'}OLD_S" ] && kill -9 "${'$'}OLD_S" 2>/dev/null || true
                rm -f '${ProfileContext.singboxPidPath}'
            fi
            echo "STOPPED"
        """.trimIndent()
        return su(cmd)
    }

    suspend fun isRunning(): Boolean {
        val res = su(
            """
            if [ -f '${ProfileContext.singboxPidPath}' ] && kill -0 "$(cat '${ProfileContext.singboxPidPath}' 2>/dev/null)" 2>/dev/null; then
                echo "YES"
            fi
            """.trimIndent()
        )
        return res.out == "YES"
    }

    suspend fun refreshRulesNow(): CmdResult {
        val cmd = """
            if [ -f '${ProfileContext.enabledFlagPath}' ] && [ -f '${ProfileContext.daemonScriptPath}' ]; then
                sh '${ProfileContext.daemonScriptPath}' --apply-only >/dev/null 2>&1
            fi
        """.trimIndent()
        return su(cmd)
    }

    private fun buildLiveDaemonScript(slot: ProxySlot, settings: ProfileSettings): String {
        val u = ProfileContext.userId
        val chain = "NP_U$u"
        val divChain = "NP_DIV_U$u"
        val uidRange = "${ProfileContext.uidStart}-${ProfileContext.uidEnd}"
        val bypassHex = "0x%x".format(ProfileContext.outboundBypassMark)
        val tproxyPort = ProfileContext.tproxyPort
        val table = ProfileContext.routingTable
        val mark = ProfileContext.tproxyMark
        val hotspot = settings.shareHotspot
        val tcpOnlyProxy = slot.protocol in setOf(
            ProxyProtocol.HTTP,
            ProxyProtocol.HTTPS,
            ProxyProtocol.SOCKS4,
            ProxyProtocol.SOCKS4A
        )

        return """
            #!/system/bin/sh
            IPT="iptables -w 100"
            IP6T="ip6tables -w 100"

            get_bin() {
                if '${ProfileContext.BIN_PATH}' version >/dev/null 2>&1; then
                    echo '${ProfileContext.BIN_PATH}'
                else
                    mkdir -p '${ProfileContext.FALLBACK_BIN_DIR}'
                    cp -f '${ProfileContext.BIN_PATH}' '${ProfileContext.FALLBACK_BIN_PATH}'
                    chmod 755 '${ProfileContext.FALLBACK_BIN_PATH}'
                    echo '${ProfileContext.FALLBACK_BIN_PATH}'
                fi
            }

            wait_for_listener() {
                PORT_HEX=$(printf '%04X' $tproxyPort)
                TRIES=0
                while [ ${'$'}TRIES -lt 25 ]; do
                    if grep -qi ":${'$'}PORT_HEX " /proc/net/tcp /proc/net/tcp6 2>/dev/null; then
                        return 0
                    fi
                    sleep 0.08
                    TRIES=$((TRIES + 1))
                done
                return 0
            }

            apply_rules() {
                # High-priority policy routing (pref 100+uid) above Android netd rules
                ip rule show | grep -q "fwmark $mark lookup $table" || ip rule add fwmark $mark table $table pref $((100 + $u))
                ip route replace local 0.0.0.0/0 dev lo table $table

                # Multi-user safe DIVERT chain for established transparent sockets (xt_socket fast-path)
                ${'$'}IPT -t mangle -N $divChain 2>/dev/null || ${'$'}IPT -t mangle -F $divChain
                ${'$'}IPT -t mangle -A $divChain -m mark ! --mark 0x0 -j ACCEPT
                ${'$'}IPT -t mangle -A $divChain -j MARK --set-mark $mark
                ${'$'}IPT -t mangle -A $divChain -j ACCEPT
                ${'$'}IPT -t mangle -C PREROUTING -p tcp -m socket --transparent -j $divChain 2>/dev/null || \
                    ${'$'}IPT -t mangle -I PREROUTING 1 -p tcp -m socket --transparent -j $divChain 2>/dev/null || true
                ${'$'}IPT -t mangle -C PREROUTING -p udp -m socket --transparent -j $divChain 2>/dev/null || \
                    ${'$'}IPT -t mangle -I PREROUTING 2 -p udp -m socket --transparent -j $divChain 2>/dev/null || true

                # IPv4 Mangle TProxy chain: capture port 53 BEFORE private subnet bypasses (catches 192.168.1.1:53 router DNS!)
                ${'$'}IPT -t mangle -N $chain 2>/dev/null || ${'$'}IPT -t mangle -F $chain
                ${'$'}IPT -t mangle -A $chain -m mark --mark $bypassHex -j RETURN
                ${'$'}IPT -t mangle -A $chain -p udp --dport 53 -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark
                ${'$'}IPT -t mangle -A $chain -p tcp --dport 53 -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark
                for SUBNET in 0.0.0.0/8 10.0.0.0/8 100.64.0.0/10 127.0.0.0/8 169.254.0.0/16 172.16.0.0/12 192.0.0.0/24 192.168.0.0/16 224.0.0.0/4 240.0.0.0/4 255.255.255.255/32; do
                    ${'$'}IPT -t mangle -A $chain -d ${'$'}SUBNET -j RETURN
                done
                ${'$'}IPT -t mangle -A $chain -p tcp -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark
                ${'$'}IPT -t mangle -A $chain -p udp -j TPROXY --on-port $tproxyPort --on-ip 127.0.0.1 --tproxy-mark $mark

                ${'$'}IPT -t mangle -C PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || \
                    ${'$'}IPT -t mangle -A PREROUTING -m mark --mark $mark -j $chain

                # Per-profile UID OUTPUT chain (port 53 marked BEFORE local subnet returns)
                ${'$'}IPT -t mangle -N ${chain}_OUT 2>/dev/null || ${'$'}IPT -t mangle -F ${chain}_OUT
                ${'$'}IPT -t mangle -A ${chain}_OUT -m mark --mark $bypassHex -j RETURN
                ${'$'}IPT -t mangle -A ${chain}_OUT -p udp --dport 53 -j MARK --set-mark $mark
                ${'$'}IPT -t mangle -A ${chain}_OUT -p tcp --dport 53 -j MARK --set-mark $mark
                for SUBNET in 0.0.0.0/8 10.0.0.0/8 100.64.0.0/10 127.0.0.0/8 169.254.0.0/16 172.16.0.0/12 192.0.0.0/24 192.168.0.0/16 224.0.0.0/4 240.0.0.0/4 255.255.255.255/32; do
                    ${'$'}IPT -t mangle -A ${chain}_OUT -d ${'$'}SUBNET -j RETURN
                done
                ${'$'}IPT -t mangle -A ${chain}_OUT -p tcp -j MARK --set-mark $mark
                ${'$'}IPT -t mangle -A ${chain}_OUT -p udp -j MARK --set-mark $mark
                ${'$'}IPT -t mangle -C OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || \
                    ${'$'}IPT -t mangle -I OUTPUT 1 -m owner --uid-owner $uidRange -j ${chain}_OUT

                # Capture Android Private DNS (netd UID 0-9999 on DoT 853 & DoH3 443)
                ${'$'}IPT -t mangle -N ${chain}_PDNS 2>/dev/null || ${'$'}IPT -t mangle -F ${chain}_PDNS
                ${'$'}IPT -t mangle -A ${chain}_PDNS -m mark --mark $bypassHex -j RETURN
                ${'$'}IPT -t mangle -A ${chain}_PDNS -p tcp --dport 853 -j MARK --set-mark $mark
                ${'$'}IPT -t mangle -A ${chain}_PDNS -p udp --dport 853 -j MARK --set-mark $mark
                for DNS_IP in 8.8.8.8 8.8.4.4 1.1.1.1 1.0.0.1 9.9.9.9 149.112.112.112; do
                    ${'$'}IPT -t mangle -A ${chain}_PDNS -d ${'$'}DNS_IP -p udp --dport 443 -j MARK --set-mark $mark
                done
                ${'$'}IPT -t mangle -C OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || \
                    ${'$'}IPT -t mangle -I OUTPUT 1 -m owner --uid-owner 0-9999 -j ${chain}_PDNS

                if [ "${if (tcpOnlyProxy) "1" else "0"}" = "1" ]; then
                    ${'$'}IPT -N ${chain}_UDPDROP 2>/dev/null || ${'$'}IPT -F ${chain}_UDPDROP
                    ${'$'}IPT -A ${chain}_UDPDROP -m mark --mark $bypassHex -j RETURN
                    ${'$'}IPT -A ${chain}_UDPDROP -p udp --dport 853 -j DROP
                    for DNS_IP in 8.8.8.8 8.8.4.4 1.1.1.1 1.0.0.1 9.9.9.9 149.112.112.112; do
                        ${'$'}IPT -A ${chain}_UDPDROP -d ${'$'}DNS_IP -p udp --dport 443 -j DROP
                    done
                    ${'$'}IPT -C OUTPUT -j ${chain}_UDPDROP 2>/dev/null || \
                        ${'$'}IPT -I OUTPUT 1 -j ${chain}_UDPDROP
                fi

                # Hotspot client forwarding (tether interfaces only, avoiding wlan0 upstream collision)
                if [ "${if (hotspot) "1" else "0"}" = "1" ]; then
                    echo 1 > /proc/sys/net/ipv4/ip_forward
                    for IFACE in ap+ swlan+ rndis+ softap+ wlan1 wlan2 bt-pan; do
                        ${'$'}IPT -t mangle -C PREROUTING -i ${'$'}IFACE -j $chain 2>/dev/null || \
                            ${'$'}IPT -t mangle -A PREROUTING -i ${'$'}IFACE -j $chain
                    done
                fi

                # IPv6 TProxy + DIVERT + Port 53 capture before local IPv6 bypasses
                ip -6 rule show | grep -q "fwmark $mark lookup $table" || ip -6 rule add fwmark $mark table $table pref $((100 + $u)) 2>/dev/null || true
                ip -6 route replace local ::/0 dev lo table $table 2>/dev/null || true
                ${'$'}IP6T -t mangle -N $divChain 2>/dev/null || ${'$'}IP6T -t mangle -F $divChain 2>/dev/null || true
                ${'$'}IP6T -t mangle -A $divChain -m mark ! --mark 0x0 -j ACCEPT 2>/dev/null || true
                ${'$'}IP6T -t mangle -A $divChain -j MARK --set-mark $mark 2>/dev/null || true
                ${'$'}IP6T -t mangle -A $divChain -j ACCEPT 2>/dev/null || true
                ${'$'}IP6T -t mangle -C PREROUTING -p tcp -m socket --transparent -j $divChain 2>/dev/null || \
                    ${'$'}IP6T -t mangle -I PREROUTING 1 -p tcp -m socket --transparent -j $divChain 2>/dev/null || true
                ${'$'}IP6T -t mangle -C PREROUTING -p udp -m socket --transparent -j $divChain 2>/dev/null || \
                    ${'$'}IP6T -t mangle -I PREROUTING 2 -p udp -m socket --transparent -j $divChain 2>/dev/null || true

                ${'$'}IP6T -t mangle -N $chain 2>/dev/null || ${'$'}IP6T -t mangle -F $chain 2>/dev/null || true
                ${'$'}IP6T -t mangle -A $chain -m mark --mark $bypassHex -j RETURN 2>/dev/null || true
                if ${'$'}IP6T -t mangle -A $chain -p udp --dport 53 -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark 2>/dev/null; then
                    ${'$'}IP6T -t mangle -A $chain -p tcp --dport 53 -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark 2>/dev/null || true
                    for SUB6 in ::/128 ::1/128 ::ffff:0:0/96 64:ff9b::/96 100::/64 2001::/32 2001:db8::/32 fc00::/7 fe80::/10 ff00::/8; do
                        ${'$'}IP6T -t mangle -A $chain -d ${'$'}SUB6 -j RETURN 2>/dev/null || true
                    done
                    ${'$'}IP6T -t mangle -A $chain -p tcp -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A $chain -p udp -j TPROXY --on-port $tproxyPort --on-ip ::1 --tproxy-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -C PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || \
                        ${'$'}IP6T -t mangle -A PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || true

                    ${'$'}IP6T -t mangle -N ${chain}_OUT 2>/dev/null || ${'$'}IP6T -t mangle -F ${chain}_OUT 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_OUT -m mark --mark $bypassHex -j RETURN 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_OUT -p udp --dport 53 -j MARK --set-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_OUT -p tcp --dport 53 -j MARK --set-mark $mark 2>/dev/null || true
                    for SUB6 in ::/128 ::1/128 fc00::/7 fe80::/10 ff00::/8; do
                        ${'$'}IP6T -t mangle -A ${chain}_OUT -d ${'$'}SUB6 -j RETURN 2>/dev/null || true
                    done
                    ${'$'}IP6T -t mangle -A ${chain}_OUT -p tcp -j MARK --set-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_OUT -p udp -j MARK --set-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -C OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || \
                        ${'$'}IP6T -t mangle -I OUTPUT 1 -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || true

                    ${'$'}IP6T -t mangle -N ${chain}_PDNS 2>/dev/null || ${'$'}IP6T -t mangle -F ${chain}_PDNS 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_PDNS -m mark --mark $bypassHex -j RETURN 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_PDNS -p tcp --dport 853 -j MARK --set-mark $mark 2>/dev/null || true
                    ${'$'}IP6T -t mangle -A ${chain}_PDNS -p udp --dport 853 -j MARK --set-mark $mark 2>/dev/null || true
                    for DNS6_IP in 2001:4860:4860::8888 2001:4860:4860::8844 2606:4700:4700::1111 2606:4700:4700::1001 2620:fe::fe 2620:fe::9; do
                        ${'$'}IP6T -t mangle -A ${chain}_PDNS -d ${'$'}DNS6_IP -p udp --dport 443 -j MARK --set-mark $mark 2>/dev/null || true
                    done
                    ${'$'}IP6T -t mangle -C OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || \
                        ${'$'}IP6T -t mangle -I OUTPUT 1 -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || true
                else
                    ${'$'}IP6T -C OUTPUT -m owner --uid-owner $uidRange ! -o lo -j DROP 2>/dev/null || \
                        ${'$'}IP6T -I OUTPUT 1 -m owner --uid-owner $uidRange ! -o lo -j DROP 2>/dev/null || true
                fi
            }

            if [ "$1" = "--apply-only" ]; then
                apply_rules
                exit 0
            fi

            echo $$ > '${ProfileContext.daemonPidPath}'
            echo $$ > /dev/cpuset/tasks 2>/dev/null || true
            echo $$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true

            RUN_BIN=$(get_bin)
            while [ -f '${ProfileContext.enabledFlagPath}' ]; do
                SPID=$(cat '${ProfileContext.singboxPidPath}' 2>/dev/null)
                if [ -z "${'$'}SPID" ] || ! kill -0 "${'$'}SPID" 2>/dev/null; then
                    "${'$'}RUN_BIN" run -c '${ProfileContext.configPath}' >> '${ProfileContext.singboxLogPath}' 2>&1 &
                    echo $! > '${ProfileContext.singboxPidPath}'
                    wait_for_listener
                fi
                apply_rules
                sleep 2
            done
        """.trimIndent()
    }

    private fun buildCleanupRulesSnippet(): String {
        val u = ProfileContext.userId
        val chain = "NP_U$u"
        val divChain = "NP_DIV_U$u"
        val uidRange = "${ProfileContext.uidStart}-${ProfileContext.uidEnd}"
        val table = ProfileContext.routingTable
        val mark = ProfileContext.tproxyMark
        return """
            IPT="iptables -w 100"
            IP6T="ip6tables -w 100"
            ${'$'}IPT -t mangle -D OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || true
            ${'$'}IPT -t mangle -D OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || true
            ${'$'}IPT -t mangle -D PREROUTING -p tcp -m socket --transparent -j $divChain 2>/dev/null || true
            ${'$'}IPT -t mangle -D PREROUTING -p udp -m socket --transparent -j $divChain 2>/dev/null || true
            ${'$'}IPT -t mangle -D PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || true
            ${'$'}IPT -D OUTPUT -j ${chain}_UDPDROP 2>/dev/null || true
            for IFACE in ap+ swlan+ rndis+ softap+ wlan1 wlan2 bt-pan wlan+; do
                ${'$'}IPT -t mangle -D PREROUTING -i ${'$'}IFACE -j $chain 2>/dev/null || true
            done
            ${'$'}IPT -t mangle -F $divChain 2>/dev/null && ${'$'}IPT -t mangle -X $divChain 2>/dev/null || true
            ${'$'}IPT -t mangle -F $chain 2>/dev/null && ${'$'}IPT -t mangle -X $chain 2>/dev/null || true
            ${'$'}IPT -t mangle -F ${chain}_OUT 2>/dev/null && ${'$'}IPT -t mangle -X ${chain}_OUT 2>/dev/null || true
            ${'$'}IPT -t mangle -F ${chain}_PDNS 2>/dev/null && ${'$'}IPT -t mangle -X ${chain}_PDNS 2>/dev/null || true
            ${'$'}IPT -F ${chain}_UDPDROP 2>/dev/null && ${'$'}IPT -X ${chain}_UDPDROP 2>/dev/null || true
            ip rule del fwmark $mark table $table 2>/dev/null || true
            ip route flush table $table 2>/dev/null || true

            ${'$'}IP6T -D OUTPUT -m owner --uid-owner $uidRange ! -o lo -j DROP 2>/dev/null || true
            ${'$'}IP6T -t mangle -D OUTPUT -m owner --uid-owner $uidRange -j ${chain}_OUT 2>/dev/null || true
            ${'$'}IP6T -t mangle -D OUTPUT -m owner --uid-owner 0-9999 -j ${chain}_PDNS 2>/dev/null || true
            ${'$'}IP6T -t mangle -D PREROUTING -p tcp -m socket --transparent -j $divChain 2>/dev/null || true
            ${'$'}IP6T -t mangle -D PREROUTING -p udp -m socket --transparent -j $divChain 2>/dev/null || true
            ${'$'}IP6T -t mangle -D PREROUTING -m mark --mark $mark -j $chain 2>/dev/null || true
            ${'$'}IP6T -t mangle -F $divChain 2>/dev/null && ${'$'}IP6T -t mangle -X $divChain 2>/dev/null || true
            ${'$'}IP6T -t mangle -F $chain 2>/dev/null && ${'$'}IP6T -t mangle -X $chain 2>/dev/null || true
            ${'$'}IP6T -t mangle -F ${chain}_OUT 2>/dev/null && ${'$'}IP6T -t mangle -X ${chain}_OUT 2>/dev/null || true
            ${'$'}IP6T -t mangle -F ${chain}_PDNS 2>/dev/null && ${'$'}IP6T -t mangle -X ${chain}_PDNS 2>/dev/null || true
            ip -6 rule del fwmark $mark table $table 2>/dev/null || true
            ip -6 route flush table $table 2>/dev/null || true
        """.trimIndent()
    }
}
