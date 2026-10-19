# Nameless Proxy (Kernel Sing-Box Tunnel)

A root-level Android kernel proxy client powered by a static `arm64` **sing-box** engine, Netfilter (`iptables` / `ip6tables` NAT, Mangle TPROXY, and Filter Kill-Switch), and Linux Policy Routing (`ip rule` / `ip -6 rule`).

## Core Capabilities
- **Direct Kernel Execution**: Runs `sing-box` natively at `/data/adb/nameless_proxy/sing-box` under Root (`UID 0`) with `SO_MARK` (`0x20000000`) routing marks — bypassing Android `VpnService` overhead and detection.
- **Multi-Protocol Engine**: Supports `SOCKS5`, `SOCKS4`, `HTTP`, `Shadowsocks` (2022/AEAD), `VLESS` (XTLS-Reality / Vision), `Trojan`, and `Hysteria 2`.
- **Zero-Leak Cellular & Wi-Fi Shield**:
  - **System `netd` DNS Hijacking**: Uses `0x20000000` outbound routing marks on `sing-box` sockets so `iptables -t nat` and `ip6tables -t nat` intercept 100% of Android `netd` (`UID 0`) and app DNS queries on both Cellular (`rmnet+`, `v4-rmnet+`, `ccmni+`) and Wi-Fi (`wlan+`) into `sing-box` FakeIP (`198.18.0.0/15`).
  - **WebRTC `connect()` / `getsockname()` Local IP Shield**: Redirects STUN/TURN (`3478:3497`, `5349:5350`, `19300:19320`) and non-DNS UDP sockets to loopback discard (`127.0.0.1:9`) inside `nat OUTPUT` and drops them at the top of `filter OUTPUT`, preventing Chromium's `getsockname()` from ever discovering cellular or Wi-Fi interface IPs.
  - **464XLAT (`clatd`) Safe IPv6 Guard**: Enforces high-priority `ip -6 rule` (`pref 8900`) `unreachable` routing for app UIDs (`10000-99999`) so IPv6 WebRTC/DNS never leaks while preserving `clatd` (`UID 1029`) on IPv6-only 4G/5G cellular carriers.
  - **Live Cellular ↔ Wi-Fi Handover Monitor**: Automatically re-asserts `rp_filter=0`, `private_dns_mode=off`, top-of-chain Netfilter hooks, and flushes DNS caches when switching between Mobile Data and Wi-Fi.
- **Native Multi-User Profiles**: Automatically isolates ports, routing tables, fwmarks, and configs per Android User ID (`User #0`, Work Profiles `User #10+`, etc.).
- **5-Slot Profile Matrix (`P0` – `P4`)**: Instant hot-swapping across 5 independent proxy slots with automatic `/data/adb/nameless_proxy` persistence and `/sdcard/Download/nameless_backup.json` export/import.
- **Auto-Startup on Boot**: Installs `/data/adb/service.d/nameless_boot.sh` for Magisk / KernelSU / APatch boot execution with `BootReceiver` fallback.
- **Hotspot & Tethering Bridge**: Transparently routes Wi-Fi Hotspot (`ap+`, `swlan+`, `softap+`, `wlan1`) and USB Tethering (`rndis+`, `usb+`, `ncm+`) client traffic through the tunnel.
- **Built-in Live IPFighter & Leak Inspector**: Ephemeral, non-cached WebView with timezone synchronization and 1-tap switching between IPFighter, BrowserLeaks WebRTC, and BrowserLeaks DNS.
- **Live Kernel Stream & Netfilter Audit**: Real-time log streaming and live `iptables` / `ip6tables` / `ip rule` inspection.
