package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    const val MAX_PROFILES = 5

    // Current active slot (0..4)
    var activeSlot: Int = 0

    // Backward-compatible slot alias
    val profileId: Int
        get() = activeSlot

    // Real Android OS User ID (e.g., 0 for main user)
    val androidUserId: Int
        get() = Process.myUid() / 100000

    // Linux UID range for current Android user (0..99999)
    val uidStart: Int
        get() = androidUserId * 100000

    val uidEnd: Int
        get() = uidStart + 99999

    // Dedicated local loopback port per profile slot (10800, 10810, 10820, etc.)
    val localInboundPort: Int
        get() = 10800 + (activeSlot * 10)

    // Dedicated local SOCKS5 inbound for internal probes (10801, 10811, 10821, etc.)
    val localMixedPort: Int
        get() = localInboundPort + 1

    // Dedicated iptables chain name per profile slot
    val chainName: String
        get() = "NAMELESS_P$activeSlot"
}
