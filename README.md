# 🚀 Nameless Proxy v1.0.0 — Official Release

Nameless Proxy is a high-performance, kernel-level transparent proxy and network isolation engine designed for rooted Android devices. Powered by the modern sing-box core and Linux kernel iptables / TPROXY routing, it delivers enterprise-grade traffic redirection without the overhead, battery drain, or restrictions of Android's standard VpnService sandbox.

---

### ✨ Key Features

• ⚡ Kernel-Level Transparent Proxying: Routes TCP via REDIRECT and UDP via TPROXY directly within the Linux netfilter layer. No VPN status bar icon, no virtual network interface overhead, and maximum throughput.
• 👥 Multi-User Profile Isolation: Leverages deterministic Linux UID range filtering (-m owner --uid-owner) to isolate traffic per user profile. Seamlessly route isolated profiles (e.g., Profile 10, Island, Shelter) through your proxy while keeping Profile 0 direct.
• 🛡️ Zero-Leak Remote DNS Engine: Enforces strict priority port 53 interception before local subnet bypasses. Upstream queries resolve remotely through the proxy tunnel over TCP, preventing ISP leakage and eliminating geo-mismatch penalties on anti-fraud platforms.
• 📡 Hotspot & Tethering Redirection: Transparently routes connected clients across Wi-Fi Hotspots, USB Tethering, and Bluetooth PAN. Includes kernel-level IPv6 drop rules to prevent connected laptops (Windows/macOS) from leaking cellular IPv6 traffic.
• 💾 Persistent Root Storage: All device configurations and upstream credentials are automatically mirrored to /data/adb/nameless_proxy/, surviving app data wipes, updates, and package uninstalls.
• ⏱️ Early Boot Execution: Automated daemon launch via /data/adb/service.d/ ensures transparent tunneling is established 5 seconds after device startup without manual intervention.
• 📊 Cyber-Tactical HUD: A responsive UI featuring decoupled symmetrical throughput gauges, dynamic fluid horizon waveforms, one-tap IP clipboard copying, and a built-in alive/dead socket diagnostic checker.

---

### 📋 System Requirements

• 📱 Operating System: Android 10 or higher.
• 🔓 Root Access: KernelSU, APatch, or Magisk (Superuser permission required).
• ⚙️ Architecture: arm64-v8a.

---

### 📥 Installation & Setup

1. Download: Get the attached NamelessProxy-v1.0.0.apk from the Assets section below.
2. Install & Grant Permissions: Install the package and grant Superuser privileges when prompted by your root manager.
3. Configure & Launch: Open the Config tab, enter your proxy credentials (SOCKS5, SOCKS4, or HTTP), verify connectivity using Check If Alive, and tap Connect Transparent Proxy.
