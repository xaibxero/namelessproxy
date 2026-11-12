package com.xaibxero.namelessproxy.core

import android.os.Process

object ProfileContext {
    val myUid: Int = Process.myUid()
    val userId: Int = myUid / 100_000
    val uidStart: Int = userId * 100_000
    val uidEnd: Int = uidStart + 99_999

    const val BASE_DIR = "/data/adb/namelessproxy"
    const val BIN_PATH = "$BASE_DIR/bin/sing-box"

    val userDir: String = "$BASE_DIR/user$userId"
    val configPath: String = "$userDir/config.json"
    val settingsPath: String = "$userDir/settings.json"
    val daemonScriptPath: String = "$userDir/live_daemon.sh"
    val enabledFlagPath: String = "$userDir/enabled"
    val bootFlagPath: String = "$userDir/boot_enabled"

    // Isolated per-profile TProxy port, DNS port, routing table, and fwmark
    val tproxyPort: Int = 12345 + userId
    val dnsPort: Int = 10530 + userId
    val routingTable: Int = 100 + userId
    val tproxyMark: String = "0x%x".format(0x1000 + userId)
    val outboundBypassMark: Int = 0x2000 + userId
}
