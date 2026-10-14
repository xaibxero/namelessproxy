package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    const val MAX_SLOTS = 5

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

    fun getMarkHex(user: Int = androidUserId, slot: Int = activeSlot): String =
        "0x" + Integer.toHexString(0x1080 + (user * 0x100) + slot)

    fun getHotspotTableId(slot: Int = activeSlot): Int =
        2000 + slot

    fun getHotspotMarkHex(slot: Int = activeSlot): String =
        "0x" + Integer.toHexString(0x1000 + slot)
}
