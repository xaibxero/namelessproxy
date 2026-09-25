package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    const val MAX_SLOTS = 5

    // In-app profile slot selected (0..4)
    var activeSlot: Int = 0

    // Backward-compatible alias
    val profileId: Int
        get() = activeSlot

    // Real Android OS User ID (0 = Owner, 10 = Work Profile, 11 = Second User, etc.)
    val androidUserId: Int
        get() = Process.myUid() / 100000

    // Unique session key for files and chains (e.g. "u0_s0", "u10_s0")
    val sessionKey: String
        get() = "u${androidUserId}_s$activeSlot"

    // Strict Linux UID boundaries for THIS Android user profile
    val uidStart: Int
        get() = androidUserId * 100000

    val uidEnd: Int
        get() = uidStart + 99999

    // Port Formula: User 0 -> 10800..10840 | User 10 -> 11800..11840 | User 11 -> 11900..11940
    val localInboundPort: Int
        get() = 10800 + (androidUserId * 100) + (activeSlot * 10)

    val localMixedPort: Int
        get() = localInboundPort + 1

    // Scoped Linux policy routing table per user and slot
    val routingTableId: Int
        get() = 1000 + (androidUserId * 10) + activeSlot

    // Scoped firewall mark per user and slot
    val markHex: String
        get() = "0x" + Integer.toHexString(0x20000 + (androidUserId * 0x100) + activeSlot)

    // Dedicated iptables chain name to prevent collisions with other Android users
    val chainName: String
        get() = "NAMELESS_U${androidUserId}_S$activeSlot"
}
