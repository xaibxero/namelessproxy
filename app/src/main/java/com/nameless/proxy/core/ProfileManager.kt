package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    const val MAX_SLOTS = 5

    // In-app profile slot selected (0..4)
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

    // User 0 -> 10800..10840 | User 10 -> 11800..11840 | User 11 -> 11900..11940
    val localInboundPort: Int
        get() = 10800 + (androidUserId * 100) + (activeSlot * 10)

    val localMixedPort: Int
        get() = localInboundPort + 1

    val routingTableId: Int
        get() = 1000 + (androidUserId * 10) + activeSlot

    val markHex: String
        get() = "0x" + Integer.toHexString(0x20000 + (androidUserId * 0x100) + activeSlot)

    val chainName: String
        get() = "NAMELESS_U${androidUserId}_S$activeSlot"
}
