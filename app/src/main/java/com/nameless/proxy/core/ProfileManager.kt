package com.nameless.proxy.core

import android.os.Process

object ProfileManager {
    // Android Profile ID calculation: UID / 100,000
    val profileId: Int
        get() = Process.myUid() / 100000

    // Range of Linux UIDs owned by this Android profile
    val uidStart: Int
        get() = profileId * 100000

    val uidEnd: Int
        get() = uidStart + 99999

    // Dedicated local loopback port per user profile
    val localInboundPort: Int
        get() = 10800 + profileId

    // Dedicated iptables chain name to prevent collisions with other profiles
    val chainName: String
        get() = "NAMELESS_U$profileId"
}
