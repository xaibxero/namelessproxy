#!/system/bin/sh
# ==============================================================================
# Nameless Proxy Root Kernel Tunnel Engine (/data/adb/nameless_proxy/tunnel.sh)
# Pure POSIX Shell • Zero App-Lifecycle Dependency • Cgroup Freezer Immune
# ==============================================================================

export PATH=/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:/system/xbin:$PATH

ADB_DIR="/data/adb/nameless_proxy"
SINGBOX_BIN="$ADB_DIR/sing-box"
STUN_BIN="$ADB_DIR/stun-responder"
CONFIG_FILE="$ADB_DIR/config.json"
SB_PID_FILE="$ADB_DIR/singbox.pid"
STUN_PID_FILE="$ADB_DIR/stun.pid"
LOG_FILE="$ADB_DIR/singbox.log"
STATE_FILE="$ADB_DIR/tunnel.state"
EXIT_IP_FILE="$ADB_DIR/exit_ip"
EXIT_JSON_FILE="$ADB_DIR/exit_info.json"

INBOUND_PORT=10800
SOCKS_PORT=10801
DNS_PORT=10802
STUN_PORT=10803
HS_DNS_PORT=10804
HS_STUN_PORT=10805
HS_INBOUND_PORT=10806
DNS_V6_PORT=10807

BOOTSTRAP_V4="9.9.9.9"
BOOTSTRAP_V6="2620:fe::fe"

CHAIN_NAT_V4="NAMELESS_NAT"
CHAIN_FILTER_V4="NAMELESS_FILTER"
CHAIN_NAT_V6="NAMELESS_V6_NAT"
CHAIN_FILTER_V6="NAMELESS_V6_FILTER"
CHAIN_HS_IN="NAMELESS_HS_IN"
CHAIN_HS_NAT="NAMELESS_HS_NAT"
CHAIN_HS_FWD="NAMELESS_HS_FWD"
CHAIN_HS_V6="NAMELESS_HS_V6"

TETHER_IFACES="ap+ swlan+ softap+ rndis+ usb+ p2p+ ncm+ wlan+"
RESERVED_V4="0.0.0.0/8 10.0.0.0/8 100.64.0.0/10 127.0.0.0/8 169.254.0.0/16 172.16.0.0/12 192.0.0.0/24 192.168.0.0/16 224.0.0.0/4 240.0.0.0/4"

clean_rules() {
    echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true
    for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do
        echo 0 > "$f" 2>/dev/null || true
    done

    for p in 7900 7901 7902 7903 8000 8001 8500 8501 8502 8503 8504 8505 8800 8801 8802 8805 8806 31000 31001 31002 31003; do
        while ip rule del pref "$p" 2>/dev/null; do :; done
    done
    ip route flush table 2088 2>/dev/null || true

    while ip -6 rule del pref 8900 2>/dev/null; do :; done
    ip -6 route flush table 2288 2>/dev/null || true

    iptables -w -D OUTPUT -j "$CHAIN_FILTER_V4" 2>/dev/null || true
    iptables -w -F "$CHAIN_FILTER_V4" 2>/dev/null || true
    iptables -w -X "$CHAIN_FILTER_V4" 2>/dev/null || true

    iptables -w -t nat -D OUTPUT -j "$CHAIN_NAT_V4" 2>/dev/null || true
    iptables -w -t nat -F "$CHAIN_NAT_V4" 2>/dev/null || true
    iptables -w -t nat -X "$CHAIN_NAT_V4" 2>/dev/null || true

    iptables -w -D INPUT -j "$CHAIN_HS_IN" 2>/dev/null || true
    iptables -w -F "$CHAIN_HS_IN" 2>/dev/null || true
    iptables -w -X "$CHAIN_HS_IN" 2>/dev/null || true

    iptables -w -D FORWARD -j "$CHAIN_HS_FWD" 2>/dev/null || true
    iptables -w -F "$CHAIN_HS_FWD" 2>/dev/null || true
    iptables -w -X "$CHAIN_HS_FWD" 2>/dev/null || true

    iptables -w -t nat -D PREROUTING -j "$CHAIN_HS_NAT" 2>/dev/null || true
    iptables -w -t nat -F "$CHAIN_HS_NAT" 2>/dev/null || true
    iptables -w -t nat -X "$CHAIN_HS_NAT" 2>/dev/null || true

    ip6tables -w -D OUTPUT -j "$CHAIN_FILTER_V6" 2>/dev/null || true
    ip6tables -w -F "$CHAIN_FILTER_V6" 2>/dev/null || true
    ip6tables -w -X "$CHAIN_FILTER_V6" 2>/dev/null || true

    ip6tables -w -t nat -D OUTPUT -j "$CHAIN_NAT_V6" 2>/dev/null || true
    ip6tables -w -t nat -F "$CHAIN_NAT_V6" 2>/dev/null || true
    ip6tables -w -t nat -X "$CHAIN_NAT_V6" 2>/dev/null || true

    ip6tables -w -D FORWARD -j "$CHAIN_HS_V6" 2>/dev/null || true
    ip6tables -w -F "$CHAIN_HS_V6" 2>/dev/null || true
    ip6tables -w -X "$CHAIN_HS_V6" 2>/dev/null || true
}

stop_tunnel() {
    rm -f "$STATE_FILE" 2>/dev/null || true
    if [ -f "$STUN_PID_FILE" ]; then
        kill -9 $(cat "$STUN_PID_FILE" 2>/dev/null) 2>/dev/null || true
        rm -f "$STUN_PID_FILE" 2>/dev/null || true
    fi
    if [ -f "$SB_PID_FILE" ]; then
        kill -9 $(cat "$SB_PID_FILE" 2>/dev/null) 2>/dev/null || true
        rm -f "$SB_PID_FILE" 2>/dev/null || true
    fi
    pkill -9 -f "nameless_daemon.sh" 2>/dev/null || true
    pkill -9 -f "RootStunDaemon" 2>/dev/null || true
    killall -9 stun-responder 2>/dev/null || true
    pkill -9 -f "stun-responder" 2>/dev/null || true
    killall -9 sing-box 2>/dev/null || true
    pkill -9 -f "sing-box" 2>/dev/null || true
    rm -f "$ADB_DIR"/running_slot* "$ADB_DIR"/running_user "$ADB_DIR"/nameless_daemon.sh "$ADB_DIR"/daemon.pid "$ADB_DIR"/stun_daemon.pid "$EXIT_IP_FILE" "$EXIT_JSON_FILE" 2>/dev/null || true

    clean_rules
    cmd connectivity flush-dns 2>/dev/null || true
    ndc resolver flushdefaultif 2>/dev/null || true
}

start_tunnel() {
    SERVER_IP="$1"
    USER_ID="${2:-0}"
    ROUTE_HOTSPOT="${3:-1}"
    SLOT_ID="${4:-0}"

    stop_tunnel
    mkdir -p "$ADB_DIR"
    rm -f "$ADB_DIR/cache.db" 2>/dev/null || true

    WAN_TABLE=$(ip rule show | awk '/^22000:/ {print $NF}' | head -n 1)
    if [ -z "$WAN_TABLE" ]; then
        WAN_TABLE=$(ip route show table all 2>/dev/null | awk '/^default via/ && $0 !~ /table (local|2088|2288)/ {for(i=1;i<=NF;i++) if($i=="table") {print $(i+1); exit}}')
    fi

    chmod 755 "$SINGBOX_BIN" "$STUN_BIN" 2>/dev/null || true
    chcon u:object_r:system_file:s0 "$SINGBOX_BIN" "$STUN_BIN" 2>/dev/null || true

    (
        trap '' HUP INT TERM
        "$SINGBOX_BIN" run -c "$CONFIG_FILE" > "$LOG_FILE" 2>&1 < /dev/null &
        echo $! > "$SB_PID_FILE"
    )
    sleep 0.25
    SB_PID=$(cat "$SB_PID_FILE" 2>/dev/null)
    if [ -z "$SB_PID" ] || ! kill -0 "$SB_PID" 2>/dev/null; then
        echo "ERROR: sing-box failed to start"
        cat "$LOG_FILE" 2>/dev/null
        stop_tunnel
        exit 1
    fi
    echo -1000 > "/proc/$SB_PID/oom_score_adj" 2>/dev/null || true
    echo "$SB_PID" > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true

    (
        trap '' HUP INT TERM
        "$STUN_BIN" "$STUN_PORT" "$HS_STUN_PORT" "$SOCKS_PORT" "$SERVER_IP" >/dev/null 2>&1 < /dev/null &
        echo $! > "$STUN_PID_FILE"
    )
    ST_PID=$(cat "$STUN_PID_FILE" 2>/dev/null)
    if [ -n "$ST_PID" ]; then
        echo -1000 > "/proc/$ST_PID/oom_score_adj" 2>/dev/null || true
        echo "$ST_PID" > /sys/fs/cgroup/cgroup.procs 2>/dev/null || true
    fi

    echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true
    for f in /proc/sys/net/ipv4/conf/*/rp_filter; do echo 0 > "$f" 2>/dev/null || true; done
    echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv4/conf/default/route_localnet 2>/dev/null || true
    for f in /proc/sys/net/ipv4/conf/*/route_localnet; do echo 1 > "$f" 2>/dev/null || true; done
    echo 1 > /proc/sys/net/ipv4/conf/all/accept_local 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv4/conf/default/accept_local 2>/dev/null || true
    for f in /proc/sys/net/ipv4/conf/*/accept_local; do echo 1 > "$f" 2>/dev/null || true; done
    echo 1 > /proc/sys/net/ipv4/tcp_fwmark_accept 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv4/fwmark_reflect 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true
    for f in /proc/sys/net/ipv6/conf/*/disable_ipv6; do echo 0 > "$f" 2>/dev/null || true; done
    conntrack -F 2>/dev/null || true

    ip rule add to 192.168.0.0/16 lookup local_network pref 7900 2>/dev/null || true
    ip rule add to 192.168.0.0/16 lookup main pref 7901 2>/dev/null || true
    ip rule add to 172.16.0.0/12 lookup local_network pref 7902 2>/dev/null || true
    ip rule add to 172.16.0.0/12 lookup main pref 7903 2>/dev/null || true

    if [ -n "$WAN_TABLE" ] && [ -n "$SERVER_IP" ]; then
        case "$SERVER_IP" in
            *:*) ;;
            *)
                ip rule add to "$SERVER_IP" lookup "$WAN_TABLE" pref 8000 2>/dev/null || true
                ip rule add to "$BOOTSTRAP_V4" lookup "$WAN_TABLE" pref 8001 2>/dev/null || true
                ;;
        esac
    fi

    APP_START=$((USER_ID * 100000 + 10000))
    APP_END=$((USER_ID * 100000 + 19999))
    SDK_START=$((USER_ID * 100000 + 20000))
    SDK_END=$((USER_ID * 100000 + 29999))
    ISO_START=$((USER_ID * 100000 + 90000))
    ISO_END=$((USER_ID * 100000 + 99999))

    ip route replace default dev lo src 127.0.0.1 table 2088 2>/dev/null || true
    ip rule add uidrange "$APP_START-$APP_END" lookup 2088 pref 8800 2>/dev/null || true
    ip rule add uidrange "$SDK_START-$SDK_END" lookup 2088 pref 8801 2>/dev/null || true
    ip rule add uidrange "$ISO_START-$ISO_END" lookup 2088 pref 8802 2>/dev/null || true
    ip rule add from 127.0.0.0/8 lookup 2088 pref 8805 2>/dev/null || true
    ip rule add to 198.18.0.0/15 lookup 2088 pref 8806 2>/dev/null || true

    ip -6 route replace unreachable default table 2288 2>/dev/null || ip -6 route replace unreachable ::/0 table 2288 2>/dev/null || true
    ip -6 rule add uidrange "$APP_START-$APP_END" lookup 2288 pref 8900 2>/dev/null || true
    ip -6 rule add uidrange "$SDK_START-$SDK_END" lookup 2288 pref 8900 2>/dev/null || true
    ip -6 rule add uidrange "$ISO_START-$ISO_END" lookup 2288 pref 8900 2>/dev/null || true

    iptables -w -N "$CHAIN_HS_IN" 2>/dev/null || true
    iptables -w -A "$CHAIN_HS_IN" -i lo -j ACCEPT 2>/dev/null || true
    for p in "$INBOUND_PORT" "$SOCKS_PORT" "$DNS_PORT" "$STUN_PORT" "$HS_DNS_PORT" "$HS_STUN_PORT" "$HS_INBOUND_PORT"; do
        iptables -w -A "$CHAIN_HS_IN" -p tcp --dport "$p" -j ACCEPT 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_IN" -p tcp --sport "$p" -j ACCEPT 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_IN" -p udp --dport "$p" -j ACCEPT 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_IN" -p udp --sport "$p" -j ACCEPT 2>/dev/null || true
    done
    iptables -w -I INPUT 1 -j "$CHAIN_HS_IN" 2>/dev/null || true

    iptables -w -t nat -N "$CHAIN_NAT_V4" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -d 127.0.0.0/8 -j RETURN 2>/dev/null || true
    for p in "$INBOUND_PORT" "$SOCKS_PORT" "$HS_INBOUND_PORT" "$DNS_PORT" "$HS_DNS_PORT" "$STUN_PORT" "$HS_STUN_PORT"; do
        iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --sport "$p" -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --sport "$p" -j RETURN 2>/dev/null || true
    done
    iptables -w -t nat -A "$CHAIN_NAT_V4" -m addrtype --dst-type LOCAL -j RETURN 2>/dev/null || true

    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp -d "$BOOTSTRAP_V4" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp -d "$BOOTSTRAP_V4" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    if [ -n "$SERVER_IP" ]; then
        case "$SERVER_IP" in
            *:*) ;;
            *) iptables -w -t nat -A "$CHAIN_NAT_V4" -d "$SERVER_IP" -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true ;;
        esac
    fi

    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT" 2>/dev/null || true

    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 80 -j REDIRECT --to-ports 9 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 443 -j REDIRECT --to-ports 9 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 853 -j REDIRECT --to-ports 9 2>/dev/null || true

    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 3478:3497 -j REDIRECT --to-ports "$STUN_PORT" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 5349:5350 -j REDIRECT --to-ports "$STUN_PORT" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp --dport 19300:19320 -j REDIRECT --to-ports "$STUN_PORT" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 3478:3497 -j RETURN 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 5349:5350 -j RETURN 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 19300:19320 -j RETURN 2>/dev/null || true

    iptables -w -t nat -A "$CHAIN_NAT_V4" -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports "$INBOUND_PORT" 2>/dev/null || true
    iptables -w -t nat -A "$CHAIN_NAT_V4" -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports "$STUN_PORT" 2>/dev/null || true

    for r in $RESERVED_V4; do
        iptables -w -t nat -A "$CHAIN_NAT_V4" -d "$r" -j RETURN 2>/dev/null || true
    done

    if [ "$(settings get global private_dns_mode 2>/dev/null)" = "hostname" ]; then
        iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 853 -j REDIRECT --to-ports "$INBOUND_PORT" 2>/dev/null || true
    else
        iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp --dport 853 -j RETURN 2>/dev/null || true
    fi

    iptables -w -t nat -A "$CHAIN_NAT_V4" -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true

    for rng in "$APP_START-$APP_END" "$SDK_START-$SDK_END" "$ISO_START-$ISO_END"; do
        iptables -w -t nat -A "$CHAIN_NAT_V4" -p tcp -m owner --uid-owner "$rng" -j REDIRECT --to-ports "$INBOUND_PORT" 2>/dev/null || true
    done
    iptables -w -t nat -I OUTPUT 1 -j "$CHAIN_NAT_V4" 2>/dev/null || true

    ip6tables -w -t nat -N "$CHAIN_NAT_V6" 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -s ::1/128 -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -d ::1/128 -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p udp --sport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p tcp --sport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p udp -d "$BOOTSTRAP_V6" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p tcp -d "$BOOTSTRAP_V6" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p udp --dport 53 -j REDIRECT --to-ports "$DNS_V6_PORT" 2>/dev/null || true
    ip6tables -w -t nat -A "$CHAIN_NAT_V6" -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_V6_PORT" 2>/dev/null || true
    ip6tables -w -t nat -I OUTPUT 1 -j "$CHAIN_NAT_V6" 2>/dev/null || true

    iptables -w -N "$CHAIN_FILTER_V4" 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -o lo -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -d 127.0.0.0/8 -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -s 127.0.0.0/8 -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -m addrtype --dst-type LOCAL -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p udp -d "$BOOTSTRAP_V4" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p tcp -d "$BOOTSTRAP_V4" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" ! -o lo -p udp --dport 53 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" ! -o lo -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" ! -o lo -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" ! -o lo -p udp --dport 853 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -m state --state ESTABLISHED,RELATED -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p udp --dport 9 -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p tcp --dport 9 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p tcp --dport 3478:3497 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p tcp --dport 5349:5350 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p tcp --dport 19300:19320 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    if [ -n "$SERVER_IP" ]; then
        case "$SERVER_IP" in
            *:*) ;;
            *) iptables -w -A "$CHAIN_FILTER_V4" -d "$SERVER_IP" -j RETURN 2>/dev/null || true ;;
        esac
    fi
    iptables -w -A "$CHAIN_FILTER_V4" -p udp --dport 67:68 -j RETURN 2>/dev/null || true
    iptables -w -A "$CHAIN_FILTER_V4" -p udp --dport 123 -j RETURN 2>/dev/null || true
    for r in $RESERVED_V4; do
        iptables -w -A "$CHAIN_FILTER_V4" -d "$r" -j RETURN 2>/dev/null || true
    done
    iptables -w -A "$CHAIN_FILTER_V4" -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true
    for rng in "$APP_START-$APP_END" "$SDK_START-$SDK_END" "$ISO_START-$ISO_END"; do
        iptables -w -A "$CHAIN_FILTER_V4" -p udp -m owner --uid-owner "$rng" -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true
        iptables -w -A "$CHAIN_FILTER_V4" -m owner --uid-owner "$rng" -j DROP 2>/dev/null || true
    done
    iptables -w -I OUTPUT 1 -j "$CHAIN_FILTER_V4" 2>/dev/null || true

    ip6tables -w -N "$CHAIN_FILTER_V6" 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -o lo -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -d ::1/128 -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -s ::1/128 -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p udp --dport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p tcp --dport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p udp --sport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p tcp --sport "$DNS_V6_PORT" -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p udp -d "$BOOTSTRAP_V6" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p tcp -d "$BOOTSTRAP_V6" --dport 53 -m owner --uid-owner 0 -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" ! -o lo -p udp --dport 53 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" ! -o lo -p tcp --dport 53 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p tcp --dport 853 -j REJECT --reject-with tcp-reset 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p udp --dport 853 -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p ipv6-icmp -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -p udp --dport 546:547 -j RETURN 2>/dev/null || true
    ip6tables -w -A "$CHAIN_FILTER_V6" -m owner --uid-owner 0-9999 -j RETURN 2>/dev/null || true
    for rng in "$APP_START-$APP_END" "$SDK_START-$SDK_END" "$ISO_START-$ISO_END"; do
        ip6tables -w -A "$CHAIN_FILTER_V6" -p tcp -m owner --uid-owner "$rng" -j REJECT --reject-with tcp-reset 2>/dev/null || true
        ip6tables -w -A "$CHAIN_FILTER_V6" -p udp -m owner --uid-owner "$rng" -j REJECT --reject-with icmp6-port-unreachable 2>/dev/null || true
        ip6tables -w -A "$CHAIN_FILTER_V6" -m owner --uid-owner "$rng" -j DROP 2>/dev/null || true
    done
    ip6tables -w -I OUTPUT 1 -j "$CHAIN_FILTER_V6" 2>/dev/null || true

    if [ "$ROUTE_HOTSPOT" = "1" ]; then
        ip6tables -w -N "$CHAIN_HS_V6" 2>/dev/null || true
        for iface in $TETHER_IFACES; do
            ip6tables -w -A "$CHAIN_HS_V6" -i "$iface" -j DROP 2>/dev/null || true
            ip6tables -w -A "$CHAIN_HS_V6" -o "$iface" -j DROP 2>/dev/null || true
        done
        ip6tables -w -I FORWARD 1 -j "$CHAIN_HS_V6" 2>/dev/null || true

        iptables -w -t nat -N "$CHAIN_HS_NAT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -i lo -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -i rmnet+ -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -i v4-rmnet+ -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -i ccmni+ -j RETURN 2>/dev/null || true
        if [ -n "$SERVER_IP" ]; then
            case "$SERVER_IP" in
                *:*) ;;
                *)
                    iptables -w -t nat -A "$CHAIN_HS_NAT" -d "$SERVER_IP" -j RETURN 2>/dev/null || true
                    iptables -w -t nat -A "$CHAIN_HS_NAT" -s "$SERVER_IP" -j RETURN 2>/dev/null || true
                    ;;
            esac
        fi
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 67:68 -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 53 -j REDIRECT --to-ports "$HS_DNS_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p tcp --dport 53 -j REDIRECT --to-ports "$HS_DNS_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 80 -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 443 -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 853 -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p tcp --dport 853 -j RETURN 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 3478:3497 -j REDIRECT --to-ports "$HS_STUN_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 5349:5350 -j REDIRECT --to-ports "$HS_STUN_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp --dport 19300:19320 -j REDIRECT --to-ports "$HS_STUN_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p udp -d 198.18.0.0/15 -j REDIRECT --to-ports "$HS_STUN_PORT" 2>/dev/null || true
        iptables -w -t nat -A "$CHAIN_HS_NAT" -p tcp -d 198.18.0.0/15 -j REDIRECT --to-ports "$HS_INBOUND_PORT" 2>/dev/null || true
        for r in $RESERVED_V4; do
            iptables -w -t nat -A "$CHAIN_HS_NAT" -d "$r" -j RETURN 2>/dev/null || true
        done
        for iface in $TETHER_IFACES; do
            iptables -w -t nat -A "$CHAIN_HS_NAT" -i "$iface" -p tcp -j REDIRECT --to-ports "$HS_INBOUND_PORT" 2>/dev/null || true
        done
        iptables -w -t nat -I PREROUTING 1 -j "$CHAIN_HS_NAT" 2>/dev/null || true

        iptables -w -N "$CHAIN_HS_FWD" 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_FWD" -i rmnet+ -j RETURN 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_FWD" -i v4-rmnet+ -j RETURN 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_FWD" -i ccmni+ -j RETURN 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_FWD" -p udp --dport 67:68 -j ACCEPT 2>/dev/null || true
        iptables -w -A "$CHAIN_HS_FWD" -p udp --sport 67:68 -j ACCEPT 2>/dev/null || true
        for iface in $TETHER_IFACES; do
            iptables -w -A "$CHAIN_HS_FWD" -i "$iface" -p tcp -j REJECT --reject-with tcp-reset 2>/dev/null || true
            iptables -w -A "$CHAIN_HS_FWD" -i "$iface" -p udp -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true
            iptables -w -A "$CHAIN_HS_FWD" -i "$iface" -j DROP 2>/dev/null || true
        done
        iptables -w -I FORWARD 1 -j "$CHAIN_HS_FWD" 2>/dev/null || true
    fi

    echo "${USER_ID}:${SLOT_ID}" > "$STATE_FILE"
    cmd connectivity flush-dns 2>/dev/null || true
    ndc resolver flushdefaultif 2>/dev/null || true
    echo "OK"
}

status_tunnel() {
    if [ -f "$SB_PID_FILE" ] && kill -0 $(cat "$SB_PID_FILE" 2>/dev/null) 2>/dev/null; then
        PID=$(cat "$SB_PID_FILE" 2>/dev/null)
        ST=$(cat "$STATE_FILE" 2>/dev/null || echo "0:0")
        IP=$(cat "$EXIT_IP_FILE" 2>/dev/null || echo "")
        echo "RUNNING|$PID|$ST|$IP"
    else
        echo "STOPPED"
    fi
}

case "$1" in
    start)
        start_tunnel "$2" "$3" "$4" "$5"
        ;;
    stop)
        stop_tunnel
        echo "STOPPED"
        ;;
    status)
        status_tunnel
        ;;
    logs)
        echo "=== NAMELESS ROOT ENGINE ==="
        if [ -f "$SB_PID_FILE" ] && kill -0 $(cat "$SB_PID_FILE" 2>/dev/null) 2>/dev/null; then
            echo "sing-box       : ONLINE (PID $(cat "$SB_PID_FILE"))"
        else
            echo "sing-box       : STANDBY"
        fi
        if [ -f "$STUN_PID_FILE" ] && kill -0 $(cat "$STUN_PID_FILE" 2>/dev/null) 2>/dev/null; then
            echo "stun-responder : ONLINE (PID $(cat "$STUN_PID_FILE"))"
        else
            echo "stun-responder : STANDBY"
        fi
        echo "Exit IP        : $(cat "$EXIT_IP_FILE" 2>/dev/null || echo 'Standby')"
        echo "Private DNS    : $(settings get global private_dns_mode 2>/dev/null)"
        echo ""
        echo "=== SING-BOX LOG STREAM ==="
        if [ -f "$LOG_FILE" ]; then
            tail -n 100 "$LOG_FILE"
        else
            echo "No log entries."
        fi
        ;;
    audit)
        echo "=== IP RULES ==="
        ip rule show | grep -E "7900|7901|8000|8001|8800|8805|8806|22000" || true
        echo ""
        echo "=== NAT (${CHAIN_NAT_V4}) ==="
        iptables -w -t nat -S "$CHAIN_NAT_V4" 2>&1 || true
        echo ""
        echo "=== HOTSPOT NAT (${CHAIN_HS_NAT}) ==="
        iptables -w -t nat -S "$CHAIN_HS_NAT" 2>&1 || true
        echo ""
        echo "=== FILTER (${CHAIN_FILTER_V4}) ==="
        iptables -w -S "$CHAIN_FILTER_V4" 2>&1 || true
        ;;
    clear_logs)
        : > "$LOG_FILE" 2>/dev/null || true
        ;;
esac
