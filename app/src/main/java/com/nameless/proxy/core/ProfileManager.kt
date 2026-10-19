package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    const val MAX_SLOTS = 5

    // Bit 29 (0x20000000 = 536870912):
    // Keeps lower 16 bits == 0x0000 so Android netd's default network rule
    // (pref 22000: fwmark 0x0/0xffff lookup <wlan0|rmnet_data0>) still routes
    // sing-box outbound sockets seamlessly across both Wi-Fi and Cellular,
    // while allowing iptables/ip6tables to distinguish sing-box (UID 0 + mark 0x20000000)
    // from Android's netd DNS resolver (UID 0 without mark 0x20000000).
    const val SINGBOX_BYPASS_MARK_INT = 0x20000000
    const val SINGBOX_BYPASS_MARK_HEX = "0x20000000/0x20000000"

    @Volatile
    var activeSlot: Int = 0

    val profileId: Int
        get() = activeSlot

    val androidUserId: Int
        get() = Process.myUid() / 100000

    val sessionKey: String
        get() = "u${androidUserId}_s$activeSlot"

    val uidStart: Int
        get() = androidUserId * 100000

    val uidEnd: Int
        get() = uidStart + 99999

    // User 0 -> 10800..10840 | User 10 -> 11800..11840
    val localInboundPort: Int
        get() = 10800 + (androidUserId * 100) + (activeSlot * 10)

    val localMixedPort: Int
        get() = localInboundPort + 1

    fun getTableId(user: Int = androidUserId, slot: Int = activeSlot): Int =
        2080 + (user * 10) + slot

    fun getV6BlockTableId(user: Int = androidUserId, slot: Int = activeSlot): Int =
        2280 + (user * 10) + slot

    fun getMarkHex(user: Int = androidUserId, slot: Int = activeSlot): String =
        "0x" + Integer.toHexString(0x1080 + (user * 0x100) + slot)

    fun getHotspotTableId(slot: Int = activeSlot): Int =
        2000 + slot

    fun getHotspotMarkHex(slot: Int = activeSlot): String =
        "0x" + Integer.toHexString(0x1000 + slot)
}
