#!/system/bin/sh
# ==============================================================================
# NamelessProxy v35 — Root Transparent Proxy, Per-App Split & WebRTC/DNS Shield
# ==============================================================================

WORK_DIR="${WORK_DIR:-/data/adb/nameless_u0}"
CONFIG_FILE="$WORK_DIR/config.json"
ENV_FILE="$WORK_DIR/tunnel.env"
PID_FILE="$WORK_DIR/singbox.pid"
STUN_PID_FILE="$WORK_DIR/stun.pid"
WATCHER_PID_FILE="$WORK_DIR/watcher.pid"
LOG_FILE="$WORK_DIR/tunnel.log"
SINGBOX_BIN="$WORK_DIR/libtun2socks.so"
STUN_BIN="$WORK_DIR/libstun.so"

REDIRECT_PORT=10800
DIRECT_REDIRECT_PORT=10808
TPROXY_PORT=10801
STUN_PORT=10803
STUN_CTRL_PORT=10804
STUN_HS_PORT=10805
MIXED_PORT=10809
DNS_PORT=10853
ROUTING_MARK=254
HS_ROUTE_TABLE=2089

CHAIN_NAT_V4="NAMELESS_NAT"
CHAIN_FLT_V4="NAMELESS_FLT"
CHAIN_INP_V4="NAMELESS_INP"
CHAIN_HS_NAT="NAMELESS_HS_NAT"
CHAIN_HS_FLT="NAMELESS_HS_FLT"
CHAIN_FLT_V6="NAMELESS_V6_FLT"

IPT="iptables -w 2"
IP6T="ip6tables -w 2"

log_msg() {
    echo "[$(date '+%H:%M:%S')] $*" >> "$LOG_FILE" 2>/dev/null
}

load_env() {
    PROXY_HOST=""
    PROXY_PORT=""
    PROXY_TYPE="socks"
    APP_MODE="global"
    UID_LIST=""
    HOTSPOT_SHARE="1"
    KILL_SWITCH="0"
    USER_ID="0"
    SPOOF_IP=""
    if [ -f "$ENV_FILE" ]; then
        . "$ENV_FILE"
    fi
}

cleanup_other_profiles() {
    for dir in /data/adb/nameless_u*; do
        [ -d "$dir" ] || continue
        if [ "$dir" != "$WORK_DIR" ]; then
            for pf in "$dir/watcher.pid" "$dir/stun.pid" "$dir/singbox.pid"; do
                if [ -f "$pf" ]; then
                    old_pid=$(cat "$pf" 2>/dev/null)
                    [ -n "$old_pid" ] && kill -9 "$old_pid" 2>/dev/null
                    rm -f "$pf" 2>/dev/null
                fi
            done
        fi
    done
}

enable_localnet_routing() {
    sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1
    sysctl -w net.ipv4.conf.all.route_localnet=1 >/dev/null 2>&1
    sysctl -w net.ipv4.conf.default.route_localnet=1 >/dev/null 2>&1
    sysctl -w net.ipv4.conf.all.rp_filter=0 >/dev/null 2>&1
    sysctl -w net.ipv4.conf.default.rp_filter=0 >/dev/null 2>&1
    for f in /proc/sys/net/ipv4/conf/*/route_localnet; do
        [ -f "$f" ] && echo 1 > "$f" 2>/dev/null
    done
    for f in /proc/sys/net/ipv4/conf/*/rp_filter; do
        [ -f "$f" ] && echo 0 > "$f" 2>/dev/null
    done
}

discover_hotspot_ifaces() {
    found=""
    for cand in wlan1 wlan2 wlan3 ap0 ap1 softap0 swlan0 swlan1 rndis0 usb0 ncm0 eth0 p2p-wlan0-0; do
        if ip -4 addr show dev "$cand" 2>/dev/null | grep -q "inet "; then
            found="$found $cand"
        fi
    done
    extra=$(ip -4 route show table all 2>/dev/null | awk '
        /^(192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|10\.)/ {
            dev="";
            for (i=1; i<=NF; i++) {
                if ($i == "dev" && (i+1)<=NF) dev=$(i+1);
            }
            if (dev != "" && dev !~ /^(wlan0|rmnet|ccmni|v4-|lo|tun|dummy|sit|ip6tnl)/) {
                print dev;
            }
        }
    ' | sort -u)
    for d in $extra; do
        case " $found " in
            *" $d "*) ;;
            *) found="$found $d" ;;
        esac
    done
    echo "$found"
}

sync_hotspot_routes() {
    # Ensure tether/hotspot subnets are always reachable before Android per-network policy rules
    for pr in 7895 7896 7897; do
        ip -4 rule del pref "$pr" >/dev/null 2>&1
    done
    ip -4 route flush table "$HS_ROUTE_TABLE" >/dev/null 2>&1

    ip -4 route show table all 2>/dev/null | awk '
        /^(192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|10\.)/ && /scope link/ {
            net=$1; dev=""; src="";
            for (i=2; i<=NF; i++) {
                if ($i == "dev" && (i+1)<=NF) dev=$(i+1);
                if ($i == "src" && (i+1)<=NF) src=$(i+1);
            }
            if (net != "" && dev != "" && dev !~ /^(lo|dummy|tun)/) {
                print net, dev, src;
            }
        }
    ' | sort -u | while read -r net dev src; do
        [ -z "$net" ] || [ -z "$dev" ] && continue
        if [ -n "$src" ]; then
            ip -4 route replace "$net" dev "$dev" proto kernel scope link src "$src" table "$HS_ROUTE_TABLE" >/dev/null 2>&1
        else
            ip -4 route replace "$net" dev "$dev" proto kernel scope link table "$HS_ROUTE_TABLE" >/dev/null 2>&1
        fi
    done

    ip -4 rule add pref 7895 from all to 192.168.0.0/16 lookup "$HS_ROUTE_TABLE" >/dev/null 2>&1
    ip -4 rule add pref 7896 from all to 172.16.0.0/12 lookup "$HS_ROUTE_TABLE" >/dev/null 2>&1
    ip -4 rule add pref 7897 from all to 10.0.0.0/8 lookup "$HS_ROUTE_TABLE" >/dev/null 2>&1
}

apply_ipv6_shield() {
    ip -6 rule del pref 7888 >/dev/null 2>&1
    ip -6 rule del pref 7890 >/dev/null 2>&1
    # Keep system/root/netd/clatd (UIDs 0..1099) on main table so 464XLAT and Wi-Fi provisioning never drop
    ip -6 rule add pref 7888 uidrange 0-1099 lookup main >/dev/null 2>&1
    # Make global IPv6 (2000::/3) return ENETUNREACH for user apps (Chrome, etc.) so libwebrtc emits zero IPv6 candidates
    ip -6 rule add pref 7890 from all to 2000::/3 unreachable >/dev/null 2>&1

    $IP6T -t filter -D OUTPUT -j "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -D FORWARD -j "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -F "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -X "$CHAIN_FLT_V6" >/dev/null 2>&1

    $IP6T -t filter -N "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -o lo -j RETURN >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -m owner --uid-owner 0-1099 -j RETURN >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -d fe80::/10 -j RETURN >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -d ff00::/8 -j RETURN >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -p ipv6-icmp -j RETURN >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -p tcp -j REJECT --reject-with tcp-reset >/dev/null 2>&1
    $IP6T -t filter -A "$CHAIN_FLT_V6" -j REJECT --reject-with icmp6-adm-prohibited >/dev/null 2>&1

    $IP6T -t filter -I OUTPUT 1 -j "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -I FORWARD 1 -j "$CHAIN_FLT_V6" >/dev/null 2>&1
}

remove_ipv6_shield() {
    ip -6 rule del pref 7888 >/dev/null 2>&1
    ip -6 rule del pref 7890 >/dev/null 2>&1
    $IP6T -t filter -D OUTPUT -j "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -D FORWARD -j "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -F "$CHAIN_FLT_V6" >/dev/null 2>&1
    $IP6T -t filter -X "$CHAIN_FLT_V6" >/dev/null 2>&1
}

flush_iptables_chains() {
    $IPT -t nat -D OUTPUT -j "$CHAIN_NAT_V4" >/dev/null 2>&1
    $IPT -t nat -F "$CHAIN_NAT_V4" >/dev/null 2>&1
    $IPT -t nat -X "$CHAIN_NAT_V4" >/dev/null 2>&1

    $IPT -t filter -D OUTPUT -j "$CHAIN_FLT_V4" >/dev/null 2>&1
    $IPT -t filter -F "$CHAIN_FLT_V4" >/dev/null 2>&1
    $IPT -t filter -X "$CHAIN_FLT_V4" >/dev/null 2>&1

    $IPT -t filter -D INPUT -j "$CHAIN_INP_V4" >/dev/null 2>&1
    $IPT -t filter -F "$CHAIN_INP_V4" >/dev/null 2>&1
    $IPT -t filter -X "$CHAIN_INP_V4" >/dev/null 2>&1

    $IPT -t nat -D PREROUTING -j "$CHAIN_HS_NAT" >/dev/null 2>&1
    for hif in wlan1 wlan2 wlan3 ap0 ap1 softap0 swlan0 swlan1 rndis0 usb0 ncm0 eth0 p2p-wlan0-0; do
        $IPT -t nat -D PREROUTING -i "$hif" -j "$CHAIN_HS_NAT" >/dev/null 2>&1
    done
    $IPT -t nat -F "$CHAIN_HS_NAT" >/dev/null 2>&1
    $IPT -t nat -X "$CHAIN_HS_NAT" >/dev/null 2>&1

    $IPT -t filter -D FORWARD -j "$CHAIN_HS_FLT" >/dev/null 2>&1
    $IPT -t filter -F "$CHAIN_HS_FLT" >/dev/null 2>&1
    $IPT -t filter -X "$CHAIN_HS_FLT" >/dev/null 2>&1
}

add_stun_redirect_rules() {
    chain="$1"
    target_port="$2"
    uid_arg="$3"

    # 1. Standard STUN/TURN ports (3478-3481, 5349-5350, 19302-19309)
    if [ -n "$uid_arg" ]; then
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 3478:3481 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 5349:5350 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 19302:19309 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        # Bypass QUIC (443/8443) in NAT so filter chain rejects QUIC cleanly for TCP HTTP/2 fallback
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 443 -j RETURN >/dev/null 2>&1
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 8443 -j RETURN >/dev/null 2>&1
        # Catch STUN on any other high UDP port (1024..65535) so custom-port WebRTC tests always get spoofed Proxy Exit IP
        $IPT -t nat -A "$chain" -m owner --uid-owner "$uid_arg" -p udp --dport 1024:65535 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
    else
        $IPT -t nat -A "$chain" -p udp --dport 3478:3481 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 5349:5350 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 19302:19309 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 443 -j RETURN >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 8443 -j RETURN >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 5353 -j RETURN >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 67:68 -j RETURN >/dev/null 2>&1
        $IPT -t nat -A "$chain" -p udp --dport 1024:65535 -j REDIRECT --to-ports "$target_port" >/dev/null 2>&1
    fi
}

apply_iptables_rules() {
    load_env
    flush_iptables_chains
    enable_localnet_routing

    $IPT -t nat -N "$CHAIN_NAT_V4" >/dev/null 2>&1
    $IPT -t filter -N "$CHAIN_FLT_V4" >/dev/null 2>&1
    $IPT -t filter -N "$CHAIN_INP_V4" >/dev/null 2>&1

    # Allow local redirected & hotspot STUN/proxy traffic into local sockets
    $IPT -t filter -A "$CHAIN_INP_V4" -i lo -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p udp --dport "$STUN_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p udp --dport "$STUN_HS_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p udp --dport "$DNS_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p tcp --dport "$DNS_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p tcp --dport "$REDIRECT_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_INP_V4" -p tcp --dport "$DIRECT_REDIRECT_PORT" -j ACCEPT >/dev/null 2>&1
    $IPT -t filter -I INPUT 1 -j "$CHAIN_INP_V4" >/dev/null 2>&1

    # ==========================================================================
    # 1. Phone NAT Chain (NAMELESS_NAT)
    # ==========================================================================
    # Rule 1: Exempt sing-box's own marked outbound sockets (mark 254 / 0xfe) FIRST
    $IPT -t nat -A "$CHAIN_NAT_V4" -m mark --mark "$ROUTING_MARK" -j RETURN >/dev/null 2>&1
    $IPT -t nat -A "$CHAIN_NAT_V4" -m mark --mark 0xfe -j RETURN >/dev/null 2>&1

    # Rule 2: Exempt direct connection to the proxy server IP itself
    if [ -n "$PROXY_HOST" ]; then
        $IPT -t nat -A "$CHAIN_NAT_V4" -d "$PROXY_HOST" -j RETURN >/dev/null 2>&1
    fi

    # Rule 3: Exempt loopback (127.0.0.0/8) so local control ports (10804, 10809) never loop
    $IPT -t nat -A "$CHAIN_NAT_V4" -d 127.0.0.0/8 -j RETURN >/dev/null 2>&1

    # Rule 4: Hijack ALL port 53 DNS (BEFORE --uid-owner 0 so Android's netd UID 0/1051 is captured!)
    $IPT -t nat -A "$CHAIN_NAT_V4" -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" >/dev/null 2>&1
    $IPT -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" >/dev/null 2>&1

    # Rule 5: Exempt non-DNS root processes (UID 0)
    $IPT -t nat -A "$CHAIN_NAT_V4" -m owner --uid-owner 0 -j RETURN >/dev/null 2>&1

    # Rule 6: Exempt private LAN / link-local / multicast ranges (keep 198.18.0.0/15 FakeIP intercepted!)
    for net in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 224.0.0.0/4 255.255.255.255/32; do
        $IPT -t nat -A "$CHAIN_NAT_V4" -d "$net" -j RETURN >/dev/null 2>&1
    done

    # Rule 7: Per-App vs Global routing
    if [ "$APP_MODE" = "per_app" ] && [ -n "$UID_LIST" ]; then
        OLD_IFS="$IFS"
        IFS=","
        for uid in $UID_LIST; do
            uid=$(echo "$uid" | tr -d ' ')
            [ -z "$uid" ] && continue
            add_stun_redirect_rules "$CHAIN_NAT_V4" "$STUN_PORT" "$uid"
            $IPT -t nat -A "$CHAIN_NAT_V4" -m owner --uid-owner "$uid" -p tcp -j REDIRECT --to-ports "$REDIRECT_PORT" >/dev/null 2>&1
        done
        IFS="$OLD_IFS"

        # Unselected apps connecting to FakeIP (198.18.0.0/15) route to direct-redirect-in (10808) -> direct-out
        $IPT -t nat -A "$CHAIN_NAT_V4" -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports "$DIRECT_REDIRECT_PORT" >/dev/null 2>&1
        $IPT -t nat -A "$CHAIN_NAT_V4" -j RETURN >/dev/null 2>&1
    else
        add_stun_redirect_rules "$CHAIN_NAT_V4" "$STUN_PORT" ""
        $IPT -t nat -A "$CHAIN_NAT_V4" -p tcp -j REDIRECT --to-ports "$REDIRECT_PORT" >/dev/null 2>&1
    fi

    $IPT -t nat -I OUTPUT 1 -j "$CHAIN_NAT_V4" >/dev/null 2>&1

    # ==========================================================================
    # 2. Phone Filter Chain (NAMELESS_FLT) — Block QUIC, DoT & Un-redirected UDP
    # ==========================================================================
    $IPT -t filter -A "$CHAIN_FLT_V4" -o lo -j RETURN >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_FLT_V4" -m mark --mark "$ROUTING_MARK" -j RETURN >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_FLT_V4" -m mark --mark 0xfe -j RETURN >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_FLT_V4" -m owner --uid-owner 0 -j RETURN >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_FLT_V4" -d 127.0.0.0/8 -j RETURN >/dev/null 2>&1
    for net in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 224.0.0.0/4 255.255.255.255/32; do
        $IPT -t filter -A "$CHAIN_FLT_V4" -d "$net" -j RETURN >/dev/null 2>&1
    done

    # Block DNS-over-TLS (port 853) so Android Private DNS falls back to port 53 -> sing-box
    $IPT -t filter -A "$CHAIN_FLT_V4" -p tcp --dport 853 -j REJECT --reject-with tcp-reset >/dev/null 2>&1
    $IPT -t filter -A "$CHAIN_FLT_V4" -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1

    if [ "$APP_MODE" = "per_app" ] && [ -n "$UID_LIST" ]; then
        OLD_IFS="$IFS"
        IFS=","
        for uid in $UID_LIST; do
            uid=$(echo "$uid" | tr -d ' ')
            [ -z "$uid" ] && continue
            $IPT -t filter -A "$CHAIN_FLT_V4" -m owner --uid-owner "$uid" -p udp --dport 443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
            $IPT -t filter -A "$CHAIN_FLT_V4" -m owner --uid-owner "$uid" -p udp --dport 8443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
            $IPT -t filter -A "$CHAIN_FLT_V4" -m owner --uid-owner "$uid" -p udp -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        done
        IFS="$OLD_IFS"
    else
        $IPT -t filter -A "$CHAIN_FLT_V4" -p udp --dport 443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        $IPT -t filter -A "$CHAIN_FLT_V4" -p udp --dport 8443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        $IPT -t filter -A "$CHAIN_FLT_V4" -p udp -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
    fi

    $IPT -t filter -I OUTPUT 1 -j "$CHAIN_FLT_V4" >/dev/null 2>&1

    # ==========================================================================
    # 3. Hotspot / Tethering Chains (NAMELESS_HS_NAT & NAMELESS_HS_FLT)
    # ==========================================================================
    if [ "$HOTSPOT_SHARE" = "1" ]; then
        sync_hotspot_routes

        $IPT -t nat -N "$CHAIN_HS_NAT" >/dev/null 2>&1
        $IPT -t filter -N "$CHAIN_HS_FLT" >/dev/null 2>&1

        # Intercept Hotspot client DNS
        $IPT -t nat -A "$CHAIN_HS_NAT" -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" >/dev/null 2>&1
        $IPT -t nat -A "$CHAIN_HS_NAT" -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" >/dev/null 2>&1

        # Bypass local LAN destinations (keep 198.18.0.0/15 FakeIP intercepted!)
        if [ -n "$PROXY_HOST" ]; then
            $IPT -t nat -A "$CHAIN_HS_NAT" -d "$PROXY_HOST" -j RETURN >/dev/null 2>&1
        fi
        for net in 127.0.0.0/8 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 224.0.0.0/4 255.255.255.255/32; do
            $IPT -t nat -A "$CHAIN_HS_NAT" -d "$net" -j RETURN >/dev/null 2>&1
        done

        # Redirect Hotspot STUN UDP to STUN_HS_PORT (10805), where stun-responder listens on the Hotspot gateway IP
        add_stun_redirect_rules "$CHAIN_HS_NAT" "$STUN_HS_PORT" ""

        # Redirect all Hotspot client TCP into sing-box redirect-in (10800)
        $IPT -t nat -A "$CHAIN_HS_NAT" -p tcp -j REDIRECT --to-ports "$REDIRECT_PORT" >/dev/null 2>&1

        # Attach to all known and dynamically discovered tether interfaces
        HS_IFS=$(discover_hotspot_ifaces)
        for hif in wlan1 wlan2 ap0 ap1 softap0 swlan0 rndis0 usb0 ncm0 $HS_IFS; do
            $IPT -t nat -D PREROUTING -i "$hif" -j "$CHAIN_HS_NAT" >/dev/null 2>&1
            $IPT -t nat -I PREROUTING 1 -i "$hif" -j "$CHAIN_HS_NAT" >/dev/null 2>&1
        done

        # In FORWARD chain: block DoT (853), QUIC (443/8443), and direct un-redirected UDP leaks from Hotspot clients
        $IPT -t filter -A "$CHAIN_HS_FLT" -p tcp --dport 853 -j REJECT --reject-with tcp-reset >/dev/null 2>&1
        $IPT -t filter -A "$CHAIN_HS_FLT" -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        $IPT -t filter -A "$CHAIN_HS_FLT" -p udp --dport 443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        $IPT -t filter -A "$CHAIN_HS_FLT" -p udp --dport 8443 -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        for hif in wlan1 wlan2 ap0 ap1 softap0 swlan0 rndis0 usb0 ncm0 $HS_IFS; do
            $IPT -t filter -A "$CHAIN_HS_FLT" -i "$hif" -p udp -m addrtype ! --dst-type LOCAL -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1
        done
        $IPT -t filter -I FORWARD 1 -j "$CHAIN_HS_FLT" >/dev/null 2>&1
    fi

    apply_ipv6_shield
}

start_hotspot_watcher() {
    if [ -f "$WATCHER_PID_FILE" ]; then
        wpid=$(cat "$WATCHER_PID_FILE" 2>/dev/null)
        [ -n "$wpid" ] && kill -9 "$wpid" 2>/dev/null
        rm -f "$WATCHER_PID_FILE" 2>/dev/null
    fi
    (
        while [ -f "$PID_FILE" ]; do
            sync_hotspot_routes
            sleep 4
        done
    ) </dev/null >/dev/null 2>&1 &
    echo $! > "$WATCHER_PID_FILE"
}

stop_daemons() {
    for pf in "$WATCHER_PID_FILE" "$STUN_PID_FILE" "$PID_FILE"; do
        if [ -f "$pf" ]; then
            pid=$(cat "$pf" 2>/dev/null)
            if [ -n "$pid" ]; then
                kill -15 "$pid" 2>/dev/null
                kill -9 "$pid" 2>/dev/null
            fi
            rm -f "$pf" 2>/dev/null
        fi
    done
    killall -9 libtun2socks.so 2>/dev/null || true
    killall -9 libstun.so 2>/dev/null || true
}

start_tunnel() {
    mkdir -p "$WORK_DIR"
    : > "$LOG_FILE"
    log_msg "Starting NamelessProxy v35 in $WORK_DIR"

    cleanup_other_profiles
    stop_daemons
    flush_iptables_chains
    remove_ipv6_shield
    rm -f "$WORK_DIR/cache.db" 2>/dev/null

    load_env

    if [ ! -x "$SINGBOX_BIN" ]; then
        chmod 755 "$SINGBOX_BIN" 2>/dev/null
    fi
    if [ ! -x "$STUN_BIN" ]; then
        chmod 755 "$STUN_BIN" 2>/dev/null
    fi

    if [ ! -f "$CONFIG_FILE" ]; then
        log_msg "ERROR: Missing $CONFIG_FILE"
        exit 1
    fi

    # 1. Start sing-box with all standard descriptors detached so su pipe never hangs
    "$SINGBOX_BIN" run -c "$CONFIG_FILE" </dev/null >>"$LOG_FILE" 2>&1 &
    SB_PID=$!
    echo "$SB_PID" > "$PID_FILE"

    # Wait briefly and verify sing-box stayed alive
    sleep 1
    if ! kill -0 "$SB_PID" 2>/dev/null; then
        log_msg "ERROR: sing-box exited prematurely"
        stop_daemons
        exit 1
    fi

    # 2. Start stun-responder pointing to sing-box's local mixed-in proxy (127.0.0.1:10809)
    "$STUN_BIN" \
        -port "$STUN_PORT" \
        -hotspot-port "$STUN_HS_PORT" \
        -ctrl "127.0.0.1:$STUN_CTRL_PORT" \
        -proxy "127.0.0.1:$MIXED_PORT" \
        -ip "${SPOOF_IP:-}" </dev/null >>"$LOG_FILE" 2>&1 &
    STUN_PID=$!
    echo "$STUN_PID" > "$STUN_PID_FILE"

    # 3. Apply iptables, IPv6 shield, and Hotspot policy routing
    apply_iptables_rules

    if [ "$HOTSPOT_SHARE" = "1" ]; then
        start_hotspot_watcher
    fi

    log_msg "Tunnel started (sing-box PID=$SB_PID, stun PID=$STUN_PID)"
    exit 0
}

stop_tunnel() {
    load_env
    log_msg "Stopping NamelessProxy in $WORK_DIR"
    flush_iptables_chains
    remove_ipv6_shield
    for pr in 7895 7896 7897; do
        ip -4 rule del pref "$pr" >/dev/null 2>&1
    done
    ip -4 route flush table "$HS_ROUTE_TABLE" >/dev/null 2>&1
    stop_daemons
    log_msg "Tunnel stopped cleanly"
    exit 0
}

reload_rules() {
    load_env
    if [ -f "$PID_FILE" ]; then
        sb_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$sb_pid" ] && kill -0 "$sb_pid" 2>/dev/null; then
            apply_iptables_rules
            log_msg "Hot-reloaded iptables rules (APP_MODE=$APP_MODE, UIDS=$UID_LIST)"
            exit 0
        fi
    fi
    exit 1
}

status_tunnel() {
    if [ -f "$PID_FILE" ]; then
        sb_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$sb_pid" ] && kill -0 "$sb_pid" 2>/dev/null; then
            echo "RUNNING:$sb_pid"
            exit 0
        fi
    fi
    echo "STOPPED"
    exit 1
}

case "$1" in
    start)
        start_tunnel
        ;;
    stop)
        stop_tunnel
        ;;
    reload)
        reload_rules
        ;;
    status)
        status_tunnel
        ;;
    *)
        echo "Usage: $0 {start|stop|reload|status}"
        exit 1
        ;;
esac
