# NamelessProxy v35 (Material 3 Expressive × Deep Liquid Glass)

Root transparent proxy & zero-leak WebRTC/DNS shield for Android (`arm64-v8a`).

## Key Fixes in v35
1. **Guaranteed Startup (`sing-box 1.11.4` Strict Schema + Detached FDs)**:
   - Removed `"override_destination"` from `route.rules` (`RouteActionSniff` in `sing-box 1.11.4` only accepts `sniffer` and `timeout`, and places `sniff_override_destination: true` on `inbounds`), fixing the fatal startup crash (`json: unknown field "override_destination"`).
   - Detached `stdin`/`stdout`/`stderr` (`</dev/null >>"$LOG_FILE" 2>&1`) on all background daemons (`sing-box`, `stun-responder`, and the hotspot route watcher) so `su -c` never hangs waiting for pipe EOF.
2. **Zero WebRTC Leaks on Chrome (Phone) & Laptop (Hotspot)**:
   - Added a local `mixed-in` proxy inbound (`127.0.0.1:10809`) in `sing-box` and configured `stun-responder` to query the proxy exit IP exclusively through `http://127.0.0.1:10809` (`sing-box` -> `proxy-out`), preventing `UID 0` (`root`) from ever querying IP endpoints over the raw ISP.
   - `stun-responder` dynamically binds dedicated UDP sockets on `127.0.0.1` and every active interface IPv4 address (`wlan*`, `rmnet*`, `ap*`, `swlan*`, `rndis*`) on ports `10803` (phone) and `10805` (hotspot), guaranteeing 100% `conntrack` reverse-NAT source IP matching.
   - Global IPv6 (`2000::/3`) is marked `unreachable` via `ip -6 rule pref 7890` for user apps while exempting system UIDs `0..1099` (`clatd` `464XLAT` and `IpClient`), preventing Chrome `libwebrtc` from emitting IPv6 candidates without dropping Wi-Fi/cellular provisioning.
3. **Working Per-App Proxy Routing (`10800` Proxy + `10808` Direct)**:
   - Placed port-53 DNS redirection after `mark 254` and before `--uid-owner 0` in `NAMELESS_NAT` so Android `netd` (`UID 0`/`1051`) DNS queries are always handled by `sing-box` FakeIP (`198.18.0.0/15`).
   - Selected apps route TCP to `redirect-in` (`10800` -> `proxy-out`), while unselected apps connecting to FakeIP (`198.18.0.0/15`) route to `direct-redirect-in` (`10808` -> `direct-out` resolved via `dns-direct`).
4. **Simplified App Routing UI & Material 3 Liquid Glass Theme**:
   - Kept only a single **Select All / Deselect All** toggle button alongside the app search bar and M3 pill switches.
   - Added bubbly spring animations (`bubblePop`) and an in-app Material 3 Liquid Glass popup toast (`#glassPopup`).
